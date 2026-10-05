# GCP workflow simulator

A local test app for the shared framework—not the deployed GCP demo. No Gemini, Oracle database, managed-agent OAuth, or cloud credentials are used. Every backend result has `evidence.simulated=true`; transfers increment an in-memory counter only.

## Run

From the repository root, with Java 17+, Maven 3.9+, Node.js 22.12+ and npm:

```bash
./build_all.sh
java -jar examples/gcp-workflow-simulator/target/gcp-workflow-simulator-0.1.0-SNAPSHOT.jar
```

Open **http://127.0.0.1:8082**. The server binds to loopback and uses a fixed local test identity. Never deploy this configuration publicly.

| Step / supported prompt | Expected result |
| --- | --- |
| `List SKUs with risk of stock outages` | One compact risk table; no map, graph or transfer writes. |
| `Show the supply chain graph for SKU-500.` | Sandboxed Cytoscape MCP App with three nodes and two directed edges. Click nodes for details; pan/zoom interactively. |
| `Show the spatial hotspot map for SKU-500.` | MapLibre MCP App: Dallas/Newark markers and a connecting line, coordinate-grid background. Markers follow pan/zoom; click for warehouse/risk details. No external tiles. |
| `Suggest inventory transfers.` | A2A response containing A2UI review forms, minimum risk 70/100, maximum three. Zero writes until explicit button approval. |

Use `SKU-APAC-210` for Sydney/Singapore and `SKU-700` for Dallas/Chicago. Unknown SKUs return an error rather than another SKU's data. The UI prompt parser supports only these patterns; it ignores no arbitrary business request because unsupported prompts explicitly fail. Risk-list scores are 0–1; recommendation filtering converts to 0–100.

Expand **Protocol trace and provenance** to inspect the actual MCP/A2A request/results. `GET /simulator/diagnostics` shows recent operation audit events and the simulated write count. Restart resets this state. A2UI approval is bound to the test identity, expires after five minutes, and cannot be reused or supplied with replacement quantities.

## Test

`./build_all.sh` runs all Java unit and HTTP integration tests. Browser tests start and stop their own simulator—stop a manually launched one first:

```bash
cd examples/gcp-workflow-simulator/frontend
npx playwright install chromium
npm test
```

The browser suite captures `target/risk-list.png`, `target/graph.png`, `target/spatial.png`, and `target/a2ui-review.png`. It checks actual iframe initialization, map markers/clicks/zoom, APAC coordinates, list routing, missing-SKU rejection, no implicit writes and explicit approval. Java tests also check expiry, caller binding, replay, forged inputs, and real MCP client/server delegation through the toolkit adapter against a synthetic upstream.

The graph fixture traverses an in-memory directed graph. It is **not** a database property-graph query. For future GCP use, supply the existing managed Oracle agent backend and preserve its `GRAPH_TABLE` validation. See [integration guidance](../../docs/common-framework.md).
