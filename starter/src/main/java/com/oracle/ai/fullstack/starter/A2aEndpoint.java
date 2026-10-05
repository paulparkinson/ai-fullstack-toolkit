package com.oracle.ai.fullstack.starter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.oracle.ai.fullstack.execution.*;
import com.oracle.ai.fullstack.projection.A2uiMessages;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** A2A 0.3 JSON-RPC synchronous message/send profile. No tasks, streaming or push advertised. */
@RestController
public final class A2aEndpoint {
    private final Operations operations;
    private final CallerResolver callers;
    private final ApprovalGate approvals;
    private final String baseUrl;
    private final ObjectMapper json = new ObjectMapper();
    public A2aEndpoint(Operations operations, CallerResolver callers, ApprovalGate approvals, String baseUrl) {
        this.operations = operations; this.callers = callers; this.approvals = approvals; this.baseUrl = baseUrl;
    }
    @GetMapping("/.well-known/agent-card.json") public Map<String,Object> card() {
        var card = new LinkedHashMap<String,Object>();
        card.put("protocolVersion", "0.3.0"); card.put("name", "AI Fullstack workflow agent");
        card.put("description", "Execute registered operations; review before approved actions.");
        card.put("version", "0.1.0"); card.put("url", baseUrl + "/a2a"); card.put("preferredTransport", "JSONRPC");
        card.put("defaultInputModes", List.of("application/json")); card.put("defaultOutputModes", List.of("application/json"));
        card.put("capabilities", Map.of("streaming", false, "pushNotifications", false,
            "extensions", List.of(Map.of("uri", A2uiMessages.EXTENSION, "description", "A2UI review forms", "required", false))));
        card.put("skills", operations.bindings().stream().filter(b -> b.definition().a2a().enabled()).map(b -> {
            var d = b.definition(); return Map.of("id", d.id(), "name", d.a2a().name(), "description", d.description(), "tags", List.of("workflow"));
        }).toList());
        return card;
    }
    @PostMapping("/a2a") public Map<String,Object> send(@RequestBody JsonNode request, HttpServletRequest http) {
        Object id = request.hasNonNull("id") ? json.convertValue(request.get("id"), Object.class) : null;
        if (!"2.0".equals(request.path("jsonrpc").asText()) || id == null) return error(id, -32600, "JSON-RPC request with id required");
        if (!"message/send".equals(request.path("method").asText())) return error(id, -32601, "Method not supported");
        try {
            var message = request.path("params").path("message");
            var parts = message.path("parts");
            if (!"message".equals(message.path("kind").asText()) || !"user".equals(message.path("role").asText())
                || message.path("messageId").asText().isBlank() || !parts.isArray() || parts.size() != 1
                || !"data".equals(parts.get(0).path("kind").asText())) throw new IllegalArgumentException("One user DataPart is required");
            var data = parts.get(0).path("data");
            var ctx = new Operations.Context(callers.actor(http), UUID.randomUUID().toString());
            Operations.Result result;
            if (data.has("userAction")) {
                if (approvals == null) throw new IllegalArgumentException("Approval actions disabled");
                var action = data.path("userAction");
                String proposalId = action.path("context").path("proposalId").asText();
                if (!"approve-transfer".equals(action.path("name").asText())
                    || !action.path("surfaceId").asText().equals("review-" + proposalId)
                    || !"approve".equals(action.path("sourceComponentId").asText())
                    || action.path("context").size() != 1) throw new IllegalArgumentException("Invalid approval action");
                result = approvals.approve(proposalId, ctx);
            } else {
                if (!data.path("arguments").isObject() || data.size() != 2) throw new IllegalArgumentException("Supply operation and arguments only");
                result = operations.execute(data.path("operation").asText(),
                    json.convertValue(data.path("arguments"), new TypeReference<Map<String,Object>>() {}), Operations.Surface.A2A, ctx);
            }
            var output = new ArrayList<Map<String,Object>>();
            output.add(Map.of("kind", "data", "data", result));
            if (result.data().get("a2ui") instanceof List<?> messages)
                for (Object ui : messages) output.add(Map.of("kind", "data", "data", ui, "metadata", Map.of("mimeType", "application/json+a2ui")));
            return Map.of("jsonrpc", "2.0", "id", id, "result", Map.of("kind", "message", "role", "agent",
                "messageId", UUID.randomUUID().toString(), "parts", output));
        } catch (IllegalArgumentException e) { return error(id, -32602, e.getMessage()); }
        catch (Exception e) { return error(id, -32603, "Backend failed; no fallback attempted"); }
    }
    private static Map<String,Object> error(Object id, int code, String message) {
        var result = new LinkedHashMap<String,Object>(); result.put("jsonrpc", "2.0"); result.put("id", id);
        result.put("error", Map.of("code", code, "message", message)); return result;
    }
}
