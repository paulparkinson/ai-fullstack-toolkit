# AI Fullstack Toolkit

AI Fullstack Toolkit provides a common execution framework for **MCP, A2A, A2UI, and MCP Apps**. Register a validated business operation once, bind its backend, and opt it into the required surfaces. The existing Oracle Database MCP Toolkit remains the database execution foundation; the framework delegates to it over MCP rather than implementing another SQL engine.

![AI Fullstack Toolkit architecture](images/ai-fullstack-toolkit-architecture.png)

See [multicloud examples built with AI Fullstack Toolkit](https://github.com/paulparkinson/oracle-ai-database-multicloud) for end-to-end examples across cloud providers.

AI Fullstack Toolkit makes one business-tool definition available across four complementary AI surfaces:

| Surface | Executable framework support |
| --- | --- |
| MCP | Official Java SDK Streamable HTTP server; discovery, validated calls, structured results, resources |
| A2A | Agent card and synchronous JSON-RPC `message/send`; shares the MCP execution catalog |
| A2UI | Declarative v0.8 review form, caller-bound approval, expiry and replay rejection |
| MCP Apps | `_meta.ui.resourceUri`, bundled HTML resources, official Apps bridge, interactive Cytoscape/MapLibre views |

It deliberately has only three publishable Maven artifacts:

1. `ai-fullstack-toolkit` — framework-neutral definitions, YAML loader, executable catalog, backend SPI, provenance/audit events, approval gate, and projections.
2. `ai-fullstack-toolkit-starter` — opt-in Spring Boot protocol adapters and an allowlisted Oracle toolkit MCP client adapter.
3. `ai-fullstack-runtime` — standalone Spring Boot runtime and configuration UI.

The upstream Oracle Database MCP Toolkit remains pinned and unmodified under [`upstream/`](UPSTREAM.md). `OracleToolkitBackend` calls an authenticated, initialized MCP client connected to that server; it does not import the server implementation or bypass its database governance.

The runtime includes Oracle JDBC Driver Extensions for centralized configuration and authentication across OCI, Microsoft Azure, Google Cloud Platform (GCP), and Amazon Web Services (AWS). These providers are loaded through JDBC's standard service-provider mechanism, so existing JDBC URLs and properties can select the cloud configuration provider without application-code changes. See the [ojdbc-extensions project](https://github.com/oracle/ojdbc-extensions) for provider URL and property formats.

Database authorization and security policies remain enforced by Oracle and the upstream toolkit. This framework does not itself configure or verify Data Safe / Deep Data Security policies.

## Build the stack progressively

Each layer builds on the same operation definition: start with governed database tools, expose selected operations through agent protocols, add interactive UI surfaces, configure cloud connectivity and memory, then manage the runtime from the admin UI.

### 1. MCP Toolkit: define governed database tools

The upstream Oracle Database MCP Toolkit owns SQL execution and database governance. Define tools in its YAML configuration; keep credentials in environment variables or a secret store rather than in the file. This abbreviated example shows the structure used by the runtime's [`oracle-mcp-tools.yaml`](runtime/src/main/resources/oracle-mcp-tools.yaml):

```yaml
dataSources:
  financial-db:
    url: ${DB_URL}
    user: ${DB_USERNAME}
    password: ${DB_PASSWORD}

tools:
  find-stockout-transfer-recommendations:
    dataSource: financial-db
    description: Return governed inventory transfer recommendations.
    parameters:
      - name: minimumStockoutRisk
        type: number
        required: true
      - name: maximumRows
        type: integer
        required: true
    statement: >-
      SELECT * FROM FINANCIAL.stockout_transfer_recommendation_v
       WHERE stockout_risk_score >= :minimumStockoutRisk
         AND ROWNUM <= :maximumRows
```

The toolkit initializes an authenticated MCP client, and the full stack adapter delegates to it through `OracleToolkitBackend`. The shared framework does not replace the upstream SQL engine.

### 2. A2A: expose selected MCP tools as agent endpoints

Add an A2A exposure to the portable toolkit definition to publish an operation through the A2A agent card and `message/send`. The A2A adapter and MCP adapter use the same validated handler and backend binding:

```yaml
tools:
  find-stockout-transfer-recommendations:
    description: Return governed inventory transfer recommendations.
    inputs:
      minimumStockoutRisk: number
      maximumRows: integer
    mcp:
      enabled: true
    a2a:
      enabled: true
      name: Inventory Risk Agent
      version: 0.1.0
```

The starter publishes `/.well-known/agent-card.json` and routes A2A requests through the same operation catalog. See [framework integration and boundaries](docs/common-framework.md) for the request contract and authentication boundary.

### 3. A2UI and MCP Apps: add task-specific interfaces

Expose review and approval as A2UI, and attach interactive graph or map content as an MCP App resource. Enable each surface only for tools that use it:

```yaml
tools:
  inventory-transfer-review:
    description: Review and approve a governed inventory transfer recommendation.
    inputs:
      recommendationId: string
    a2ui:
      enabled: true
      surface: inventory-transfer-review
    mcpApp:
      enabled: true
      resource: ui://inventory-transfer/review
```

A2UI carries the review surface and explicit user action; the MCP App resource provides the interactive UI. UI metadata does not grant database access: write operations still go through the validated, authorized backend and approval gate.

### 4. Cloud configuration providers: resolve connection settings per cloud

Oracle JDBC Driver Extensions let JDBC applications use cloud-specific configuration and authentication providers without hard-coding provider logic in the application. The runtime reads connection settings from environment variables, for example:

```bash
export DB_USERNAME='app_user'
export DB_PASSWORD="$DB_SECRET_FROM_SECRET_STORE"
export DB_DSN='mydb_high'
export TNS_ADMIN='/secure/wallet'
```

For centralized configuration, use the provider-specific JDBC URL and properties from the [ojdbc-extensions project](https://github.com/oracle/ojdbc-extensions). OCI, Azure, GCP, and AWS providers are supported by the extension set; select the provider and authentication mode appropriate to the deployment. Never commit secrets or wallet material.

### 5. Agent memory: add durable context as an application backend

The toolkit's backend SPI lets an application bind an operation to an agent-memory service while keeping its definition and protocol projections consistent. For example, a memory operation can accept a user-scoped query and return only authorized context:

```yaml
tools:
  search-agent-memory:
    description: Retrieve relevant context for the authenticated user.
    inputs:
      query: string
      limit: integer
    mcp:
      enabled: true
    a2a:
      enabled: true
```

Bind this operation to the application's memory adapter and enforce identity, tenant scope, retention, and authorization in that backend. The toolkit does not provide a memory store or infer permission to retrieve another user's context.

### 6. Admin UI: configure and inspect the integrated runtime

Run the standalone runtime and open [http://localhost:8080](http://localhost:8080). The admin UI lets you inspect tool definitions and their generated MCP, A2A, A2UI, and MCP App projections, then create or replace definitions in the running registry. The demo's configuration store is in-memory, so changes reset when the runtime restarts. Use the starter integration and your deployment's persistent configuration process for production.

```bash
./build_all.sh
java -jar runtime/target/ai-fullstack-runtime-0.1.0-SNAPSHOT.jar
```

The existing configuration format below remains the portable source for these layered definitions.

## Shared execution architecture

```text
MCP tools / A2A message/send
        → Operations: exposure + input validation + server-owned backend binding
            → read Backend: existing managed Oracle agent client (future GCP wiring)
                → structured result + backend evidence → MCP App (graph/map)
            → review handler → A2UI → explicit user action → ApprovalGate
                → OracleToolkitBackend → existing Oracle MCP toolkit → database write
```

The simulator substitutes **only the backend implementations and caller identity**. Its protocol endpoints, input validation, approval gate, and MCP App bridge are real. A simple risk-list operation has no UI resource metadata, so it returns a table without launching graph/map apps. Raw evidence supplied by a model is not an accepted input.

See [framework integration and boundaries](docs/common-framework.md) for registration, upstream toolkit delegation, authentication, protocol versions, and what must be completed before a production migration.

### Existing descriptor dashboard

```mermaid
flowchart LR
  Config[Toolkit YAML / environment] --> Core[ai-fullstack-toolkit core\nDefinitions · loader · registry · projections]
  Core --> MCP[MCP descriptor]
  Core --> A2A[A2A agent card]
  Core --> A2UI[A2UI messages]
  Core --> App[MCP App resource]
  Core --> Starter[ai-fullstack-toolkit-starter\nSpring Boot auto-configuration]
  Starter --> Runtime[ai-fullstack-runtime\nSpring Boot API + database checks]
  JDBC[Oracle JDBC + cloud config providers\nOCI · Azure · GCP · AWS] --> Runtime
```

### Spring Boot starter and admin GUI

```mermaid
flowchart TB
  Admin[Administrator] --> GUI[Runtime admin GUI\nstatic HTML / CSS / JavaScript]
  GUI --> API[Spring Boot REST API\n/api/tools · database checks]
  API --> Starter[Spring Boot starter]
  Starter --> Registry[ToolRegistry]
  Registry --> Core[Core definitions and projections]
  API --> JDBC[Oracle JDBC connection]
  JDBC --> DDS[Deep Data Security\nOCI · Azure · GCP · AWS]
  JDBC --> Providers[ojdbc-extensions\ncloud configuration providers]
```

## Quick start: executable workflow simulator

Prerequisites: JDK 17+, Maven 3.9+, and Node.js 22.12+ with npm. Maven installs the lockfile-pinned frontend dependencies and bundles the UI into the simulator JAR. The build needs dependency-download access; the running simulator needs no cloud access, credentials, tile provider, or external JavaScript CDN.

```bash
./build_all.sh
java -jar examples/gcp-workflow-simulator/target/gcp-workflow-simulator-0.1.0-SNAPSHOT.jar
```

Open **http://127.0.0.1:8082**. Use the four buttons, or these supported test-host prompts:

1. `List SKUs with risk of stock outages`
2. `Show the supply chain graph for SKU-500.`
3. `Show the spatial hotspot map for SKU-500.`
4. `Suggest inventory transfers.`

Step 4 reviews at risk ≥70/100, at most three suggestions. It executes **zero writes** until you click **Approve this transfer**. Even then only an in-memory simulated write is recorded. Replace `SKU-500` with `SKU-APAC-210` or `SKU-700` to exercise distinct graph/map results; unknown SKUs fail without fallback. The prompt parser is deliberately limited and is not an LLM.

The map uses an offline coordinate grid, geographic markers and a connecting line; it is not a street-map provider or a driving-route calculation. Pan/zoom and warehouse clicks work without external network access.

### Browser tests

Stop any manually running simulator first; the tests start their own instance on port 8082.

```bash
cd examples/gcp-workflow-simulator/frontend
npx playwright install chromium
npm test
```

Automated tests cover schema/exposure checks, A2A/MCP execution, resource discovery, dynamic fixtures, no fallback, toolkit-adapter wire delegation, origin rejection, and approval expiry/replay. Browser tests cover the four-step flow, iframe initialization, clickable/moving markers, SKU changes, plain lists, and explicit approval. Screenshots are generated under the example's `target/` directory and are not committed.

These tests prove **framework behavior against simulated backends**, not a live Oracle query, property-graph execution, OAuth renewal, Gemini rendering, or a production transfer.

## Run the existing descriptor dashboard

```bash
java -jar runtime/target/ai-fullstack-runtime-0.1.0-SNAPSHOT.jar
```

Open [http://localhost:8080](http://localhost:8080). The **AI Fullstack Toolkit** dashboard displays the seeded definitions and their enabled surfaces. Select each output tab to view the generated MCP descriptor, A2A card, A2UI messages, or MCP App resource descriptor. Use **Create** to create or replace a definition in the running registry. This first runtime configuration store is intentionally in-memory: restart restores the seeded definition, which makes the demo safe to explore.

For a ChatGPT MCP App backed by Oracle Database MCP Toolkit and the public OCI FinancialDB, see [`chatgpt/`](chatgpt/README.md) and its [quick walkthrough](chatgpt/blog.html). That example has its own notes table and explicit user-confirmed insert flow.

## Seeded supply-chain surfaces

| Tool | MCP | A2A | A2UI | MCP App |
| --- | --- | --- | --- | --- |
| `inventory-transfer-a2ui` | disabled | enabled | enabled | disabled |
| `inventory-spatial-mcpapp` | enabled | disabled | disabled | enabled |
| `inventory-graph-mcpapp` | enabled | disabled | disabled | enabled |

The runtime also imports the checked-in Oracle Database MCP Java Toolkit `tools.yaml` snapshot as MCP-only definitions. This makes the SQL surface visible as `oracle-sql`, alongside the existing supply-chain toolkit entries such as `find-stockout-transfer-recommendations` and `approve-inventory-transfer`. For each imported configured tool, the MCP tab also displays its exact SQL statement or PL/SQL block. The imported configuration has placeholders only (`${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`); it contains no database secret.

## Walkthrough: inventory transfer

1. Start the runtime with the command above.
2. Select `inventory-transfer-a2ui`. Its tiles show only **A2A** and **A2UI** enabled. The A2A card has an addressable inventory-transfer skill, and the A2UI tab begins the `inventory-transfer-review` surface.
3. Select `inventory-spatial-mcpapp` or `inventory-graph-mcpapp`. Their tiles show only **MCP** and **MCP APP** enabled. Use the MCP APP tab to inspect the interactive resource URI.
4. Select `inventory-transfer-a2ui`. Its tiles show **A2A** and **A2UI** enabled, with MCP App disabled. This is the agent-driven review surface; any production write must remain behind an explicit, authenticated approval workflow.
5. Select `oracle-sql` or an imported supply-chain tool to inspect the MCP descriptor and input schema imported from the Oracle toolkit catalog.
6. Use **Create** to add a definition. The UI performs a `PUT /api/tools/{id}` and immediately regenerates all enabled projections.

Equivalent HTTP inspection:

```bash
curl -s http://localhost:8080/api/tools | jq
curl -s http://localhost:8080/api/tools/inventory-transfer-a2ui/a2a/card | jq
curl -s http://localhost:8080/api/tools/inventory-transfer-a2ui/a2ui/example | jq
curl -s http://localhost:8080/api/tools/inventory-spatial-mcpapp/mcp-app | jq
curl -s http://localhost:8080/api/tools/oracle-sql/mcp | jq
```

## Demonstrating database access

Use **DATABASE CHECK** in the dashboard, or call the equivalent endpoint:

```bash
curl -s 'http://localhost:8080/api/tools/database/spatial-demo?sku=SKU-500' | jq
```

It runs the supply-chain spatial lookup with the runtime's configured Oracle JDBC connection. A real database result is unambiguous: the response has `"sourceMode": "oracle-database"`, along with the resolved inventory locations. If it returns `"sourceMode": "seeded-demo"`, the response's `sourceDetail` identifies why the live connection was unavailable; that is intentionally not presented as a database demonstration. Configure `DB_USERNAME`, `DB_PASSWORD`, `DB_DSN`, and, for wallet connections, `TNS_ADMIN` (or `DB_WALLET_DIR`) before starting the runtime.

## Configuration format

The core can load a portable application YAML document. It is deliberately named as toolkit configuration rather than presented as an MCP standard:

```yaml
tools:
  inventory-transfer:
    description: Move SKU inventory between two locations.
    inputs:
      sku: string
      quantity: integer
    mcp:
      enabled: true
    a2a:
      enabled: true
      name: Inventory Transfer Agent
      version: 0.1.0
    a2ui:
      enabled: true
      surface: inventory-transfer-review
    mcpApp:
      enabled: true
      resource: ui://inventory-transfer/review
```

## Spring Boot integration

Add the starter to an existing service, then inject `ToolRegistry` and register a `ToolDefinition`. The registry remains independent of Spring, so the same definitions can run in a CLI, servlet, or another framework. The included [`examples/inventory-transfer-demo`](examples/inventory-transfer-demo/README.md) demonstrates the smallest useful Spring Boot service:

```bash
mvn -pl core,starter -am install -DskipTests
mvn -pl examples/inventory-transfer-demo spring-boot:run
curl -s http://localhost:8081/demo/a2a-card | jq
```

## Current scope and production boundary

The common framework now executes registered handlers; the older runtime dashboard still previews descriptors and has its own legacy JDBC demonstration. Merely creating a dashboard definition does **not** register executable code. The old JDBC demo's seeded fallback is not used by the common execution path or simulator.

Protocol adapters are disabled unless `fullstack.protocols.enabled=true`. The default caller resolver requires a container-authenticated principal. The simulator deliberately overrides that resolver and binds to loopback; **do not deploy its identity or fixtures as a production service**. Authentication/OAuth deployment, durable approvals/audit, database transaction idempotency, and live host compatibility remain application responsibilities. The in-memory approval gate prevents repeat dispatch in one process, but cannot guarantee exactly-once database execution across restarts or distributed instances.
