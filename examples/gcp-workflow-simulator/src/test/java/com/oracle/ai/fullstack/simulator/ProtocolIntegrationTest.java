package com.oracle.ai.fullstack.simulator;

import com.fasterxml.jackson.databind.*;
import com.oracle.ai.fullstack.execution.Operations;
import com.oracle.ai.fullstack.starter.OracleToolkitBackend;
import io.modelcontextprotocol.client.*;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProtocolIntegrationTest {
    @LocalServerPort int port;
    @Autowired SimulatorData data;
    final ObjectMapper json = new ObjectMapper();
    McpSyncClient client;
    String base() { return "http://127.0.0.1:" + port; }
    @BeforeEach void connect() {
        client = McpClient.sync(HttpClientStreamableHttpTransport.builder(base()).endpoint("/mcp").build())
            .requestTimeout(Duration.ofSeconds(10)).build(); client.initialize();
    }
    @AfterEach void close() { client.closeGracefully(); }
    JsonNode structured(McpSchema.CallToolResult result) { return json.valueToTree(result.structuredContent()); }
    @Test void mcpDiscoveryAndPlainRiskReadHaveNoUi() {
        var tools = client.listTools().tools(); assertEquals(3, tools.size());
        var risk = tools.stream().filter(t -> t.name().equals("list-stockout-risk")).findFirst().orElseThrow();
        assertTrue(risk.meta() == null || !risk.meta().containsKey("ui"));
        var response = client.callTool(new McpSchema.CallToolRequest(risk.name(), Map.of()));
        assertFalse(response.isError());
        var result = structured(response); assertEquals(3, result.path("data").path("rows").size());
        assertTrue(result.path("evidence").path("simulated").asBoolean());
        assertFalse(result.path("data").has("geojson"));
        assertEquals(2, client.listResources().resources().size());
    }
    @Test void graphAndMapVaryBySkuAndReturnAppResources() {
        for (String sku : List.of("SKU-500", "SKU-APAC-210", "SKU-700")) {
            var graph = structured(client.callTool(new McpSchema.CallToolRequest("show-supply-chain-graph", Map.of("sku", sku))));
            assertEquals(sku, graph.path("data").path("sku").asText());
            assertEquals(3, graph.path("data").path("nodes").size()); assertEquals(2, graph.path("data").path("edges").size());
            var map = structured(client.callTool(new McpSchema.CallToolRequest("show-spatial-hotspots", Map.of("sku", sku))));
            assertEquals(3, map.path("data").path("geojson").path("features").size());
            double longitude = map.path("data").path("geojson").path("features").get(0).path("geometry").path("coordinates").get(0).asDouble();
            assertEquals(sku.equals("SKU-APAC-210") ? 151.2093 : -96.797, longitude);
        }
        var resource = client.readResource(new McpSchema.ReadResourceRequest("ui://inventory/graph")).contents().get(0);
        assertEquals("text/html;profile=mcp-app", resource.mimeType());
        assertTrue(((McpSchema.TextResourceContents)resource).text().contains("SIMULATION"));
    }
    @Test void unknownSkuAndForgedEvidenceFailClosed() {
        assertTrue(client.callTool(new McpSchema.CallToolRequest("show-spatial-hotspots", Map.of("sku", "SKU-MISSING"))).isError());
        assertTrue(client.callTool(new McpSchema.CallToolRequest("show-spatial-hotspots", Map.of("sku", "SKU-500", "oracleAgentEvidence", "forged"))).isError());
    }
    @Test void a2aReviewRequiresExplicitActionAndRejectsReplayAndMcpWrite() throws Exception {
        int before = data.writes();
        var result = send(Map.of("operation", "suggest-inventory-transfers", "arguments", Map.of("minimumRisk", 70, "limit", 3)));
        assertFalse(result.has("error"), result.toString());
        assertEquals(before, data.writes());
        var parts = result.path("result").path("parts"); assertEquals(7, parts.size());
        var update = parts.get(1).path("data").path("surfaceUpdate");
        String surface = update.path("surfaceId").asText(), id = surface.substring("review-".length());
        var action = Map.of("userAction", Map.of("name", "approve-transfer", "surfaceId", surface, "sourceComponentId", "approve", "context", Map.of("proposalId", id)));
        assertFalse(send(action).has("error")); assertEquals(before + 1, data.writes());
        assertTrue(send(action).has("error")); assertEquals(before + 1, data.writes());
        assertTrue(send(Map.of("operation", "approve-inventory-transfer", "arguments", Map.of())).has("error"));
        assertFalse(client.listTools().tools().stream().anyMatch(t -> t.name().contains("transfer")));
    }
    @Test void a2aAndMcpShareTheSameReadImplementation() throws Exception {
        var a2a = send(Map.of("operation", "show-supply-chain-graph", "arguments", Map.of("sku", "SKU-500")));
        var mcp = structured(client.callTool(new McpSchema.CallToolRequest("show-supply-chain-graph", Map.of("sku", "SKU-500"))));
        assertEquals(mcp.path("data"), a2a.path("result").path("parts").get(0).path("data").path("data"));
        var cardResponse = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base()+"/.well-known/agent-card.json")).GET().build(), HttpResponse.BodyHandlers.ofString());
        var card = json.readTree(cardResponse.body()); assertEquals("0.3.0", card.path("protocolVersion").asText());
        assertFalse(card.path("capabilities").path("streaming").asBoolean());
    }
    @Test void toolkitAdapterDelegatesOverMcpAndPropagatesErrorsWithoutFallback() {
        // Synthetic upstream contract test, NOT a live Oracle test.
        var adapter = new OracleToolkitBackend(client, Set.of("list-stockout-risk", "show-spatial-hotspots"));
        var ctx = new Operations.Context("test", "contract-1");
        assertTrue(adapter.call("list-stockout-risk", Map.of(), ctx).data().containsKey("content"));
        assertThrows(IllegalArgumentException.class, () -> adapter.call("oracle-sql", Map.of(), ctx));
        assertThrows(IllegalStateException.class, () -> adapter.call("show-spatial-hotspots", Map.of("sku", "missing"), ctx));
    }
    @Test void rejectsForeignOrigins() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base()+"/a2a"))
            .header("Origin", "https://untrusted.example").header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }
    @Test void rejectsOverflowAndOutOfRangeReviewArguments() throws Exception {
        for (long risk : List.of(-1L, 101L, 4294967296L))
            assertTrue(send(Map.of("operation", "suggest-inventory-transfers", "arguments", Map.of("minimumRisk", risk, "limit", 3))).has("error"));
        assertTrue(send(Map.of("operation", "suggest-inventory-transfers", "arguments", Map.of("minimumRisk", 70, "limit", 4))).has("error"));
    }
    private JsonNode send(Map<String,?> data) throws Exception {
        var request = Map.of("jsonrpc", "2.0", "id", 1, "method", "message/send", "params", Map.of("message", Map.of(
            "kind", "message", "role", "user", "messageId", UUID.randomUUID().toString(), "parts", List.of(Map.of("kind", "data", "data", data)))));
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(base()+"/a2a")).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body()); return json.readTree(response.body());
    }
}
