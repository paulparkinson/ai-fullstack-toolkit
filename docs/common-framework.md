# Common protocol framework

## What is shared

`Operations` owns an immutable catalog of `Binding(ToolDefinition, Handler)` values. MCP and A2A dispatch into the same handlers. Each dispatch checks surface exposure, exact required arguments, primitive types, and rejects extra fields. Handlers return `Result(data, Evidence)`; clients cannot supply or replace evidence as an operation parameter. The injected audit consumer receives correlation ID, trusted actor, surface, operation, outcome, and backend evidence—not credentials or arbitrary argument bodies.

The existing `ToolRegistry` and YAML loader remain usable for discovery/configuration. They do not grant execution. A definition becomes executable only when application code binds a handler into `Operations`. All declared primitive inputs are required; nested schemas, optional arguments and richer business validation belong in a future schema extension or application handler. Numeric ranges must be checked in the handler.

## Register an operation

Add `com.oracle.ai.fullstack:ai-fullstack-toolkit-starter:0.1.0-SNAPSHOT` to a Spring Boot application. Enable:

```yaml
fullstack:
  protocols:
    enabled: true
  public-base-url: https://your-service.example
```

Provide `Operations`, `AppResources` (if using apps), an authenticated `CallerResolver`, and optionally `ApprovalGate` beans. The executable example is [SimulatorApplication](../examples/gcp-workflow-simulator/src/main/java/com/oracle/ai/fullstack/simulator/SimulatorApplication.java).

```java
var binding = new Operations.Binding(definition,
    (arguments, context) -> backend.call(definition.id(), arguments, context));
var operations = new Operations(List.of(binding), auditSink);
```

`definition` may come from the existing `ToolkitConfigurationLoader`. Choose exposures explicitly; never expose arbitrary SQL or a transfer writer just because it appears in an imported catalog. The `Backend` SPI lets a managed-agent read client and an Oracle MCP toolkit client remain separate while sharing transports, validation and UI resources.

## Retain the existing Oracle MCP toolkit

Run the pinned upstream server using its [own setup instructions](../upstream/oracle-db-mcp-java-toolkit/README.md). Connect an authenticated official Java MCP client and hand it to `OracleToolkitBackend`. For example, in server-side application configuration:

```java
var transport = HttpClientStreamableHttpTransport.builder(toolkitBaseUrl)
    .endpoint("/mcp")
    .httpRequestCustomizer((request, method, uri, body, context) ->
        request.header("Authorization", "Bearer " + tokenProvider.currentAccessToken()))
    .build();
var client = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
client.initialize();
var toolkit = new OracleToolkitBackend(client, Set.of("approve-inventory-transfer"));
```

`tokenProvider` is your server-side OAuth component; it is not provided by this library. Manage the MCP client's lifetime with the application and close it on shutdown. Verify the deployed upstream server's exact tool names and argument schema with `tools/list`. The adapter calls only allowlisted tools and surfaces upstream errors; it never executes JDBC itself, invents success, or substitutes fixtures. Its evidence correlation ID is local to the call: it is **not** an independent Oracle database audit record.

Attach the writer backend to `ApprovalGate`, not to ordinary MCP tool discovery. Obtain suggestions from a trusted backend, validate the exact transfer parameters, create a proposal, and render `A2uiMessages.review(proposal)`. The `userAction` contains only the proposal ID; approved quantities/locations are recovered from server-side state. Approval is caller-bound, expires, and is consumed before dispatch. If an upstream write times out, reconcile its outcome; do not blindly retry with a new proposal.

## Future GCP integration (not performed)

1. Wrap the existing Java gateway's managed Oracle AI Database Agent client as the read `Backend`. Retain its OAuth registration, token renewal, request tracing, property-graph query validation and spatial JSON checks. This repository does not recreate that cloud client or claim to validate live results.
2. Bind the plain inventory-risk query with **no MCP App metadata**. Bind graph and spatial reads to their respective `ui://` resources. Accept SKU/filter inputs only—not Gemini-created graph, coordinate or evidence objects.
3. Route transfer suggestions to the existing trusted recommendation service. Use A2A/A2UI review and the allowlisted Oracle MCP toolkit writer after explicit approval. Preserve the live system's business rules and transaction checks.
4. Reuse or replace the bundled UI resources with the GCP app's existing Cytoscape/MapLibre assets and approved basemap/CSP configuration. Do not substitute this example's fixtures/grid for the deployed app.
5. Add authenticated per-operation authorization, durable approval/audit storage, idempotent upstream transaction IDs, bounded timeouts/request sizes, and integration tests in Gemini before switching traffic. Do not infer authorization from client-supplied actor fields or from the model saying “approved.” A production human-approval channel needs its own access policy; the protocol alone does not prove human presence.

## Protocol profile

| Surface | Implementation / intentional limit |
| --- | --- |
| MCP | Official Java SDK 0.17.2; Streamable HTTP `/mcp`, initialization/session lifecycle, tools and static resources. Upstream vendored toolkit remains on its own SDK 0.12.1. Wire adapter tested with a synthetic upstream, not a live Oracle deployment. |
| A2A | Pinned 0.3.0 JSON-RPC `message/send` returning a synchronous Message; card at `/.well-known/agent-card.json`. No task persistence, cancellation, streaming or push notifications advertised. Other methods return method-not-found. |
| A2UI | Pinned v0.8 standard-catalog subset: Column, Text, Button, `surfaceUpdate`, `beginRendering`, and `userAction`. This is a compatibility profile, not a claim to implement newer A2UI versions or every component. |
| MCP Apps | `_meta.ui.resourceUri`; `text/html;profile=mcp-app` resources; official Apps SDK initialization and tool-result notifications. Bundled HTML; sandboxed iframe without same-origin access or external network in the simulator. |

A2A DataPart request contract:

```json
{
  "jsonrpc": "2.0",
  "id": "demo-1",
  "method": "message/send",
  "params": {
    "message": {
      "kind": "message",
      "role": "user",
      "messageId": "message-1",
      "parts": [{"kind": "data", "data": {
        "operation": "suggest-inventory-transfers",
        "arguments": {"minimumRisk": 70, "limit": 3}
      }}]
    }
  }
}
```

Natural-language understanding is application-owned; the shared A2A endpoint requires the explicit structured contract above. The simulator translates only its documented demo prompts into that contract. It must not be mistaken for an LLM or automatic Gemini routing.

## Security and verification

The starter requires an authenticated principal by default and rejects browser Origins that differ from `fullstack.public-base-url`. It does not install an OAuth identity provider or a blanket CORS policy. Configure TLS, authentication and authorization at the service boundary; limit endpoint request sizes/rates in production. Keep upstream credentials and refresh tokens server-side, outside YAML committed to Git. A2UI source text is rendered as text, not arbitrary HTML.

`ApprovalGate` is a bounded, five-minute-in-the-example, single-process reference store. It is intentionally not durable/distributed. Replace that storage before horizontally scaling or handling real writes. `Operations.Evidence` is backend-supplied provenance, **not cryptographic proof**; live verification requires matching the upstream request/query/audit records. The example uses `simulated=true` throughout and does not fall back from a live failure because it has no live backend at all.

References: [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk), [MCP Apps](https://apps.extensions.modelcontextprotocol.io/api/documents/Quickstart.html), [A2A 0.3 specification](https://a2a-protocol.org/v0.3.0/specification/), [A2UI v0.8 specification](https://a2ui.org/specification/v0.8-a2ui/).
