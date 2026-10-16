import { readFile } from "node:fs/promises";
import path from "node:path";
import express from "express";
import { Client } from "@modelcontextprotocol/client";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/client";
import { StdioClientTransport } from "@modelcontextprotocol/client/stdio";
import { directory, loadConfig, toolkitEnvironment } from "./config.mjs";
import { McpServer, createMcpHandler } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import { registerAppResource, registerAppTool, RESOURCE_MIME_TYPE } from "@modelcontextprotocol/ext-apps/server";
import { z } from "zod";

const upstreamUrl = process.env.ORACLE_MCP_URL ?? "https://127.0.0.1:45450/mcp";
const token = process.env.ORACLE_MCP_AUTH_TOKEN;
const port = Number(process.env.PORT ?? "3001");
const bind = process.env.MCP_BIND_HOST ?? "127.0.0.1";
const resourceUri = "ui://financialdb/chatgpt-notes-v1";
const bundle = await readFile(path.join(import.meta.dirname, "dist", "mcp-app.html"), "utf8");
const upstream = new Client({ name: "financialdb-chatgpt-app", version: "1.0.0" });
const transport = process.env.ORACLE_MCP_TRANSPORT === "stdio"
  ? new StdioClientTransport({
    command: "java",
    args: ["-Dtransport=stdio", `-DconfigFile=${path.join(directory, "toolkit-tools.yaml")}`, "-Dtools=chatgpt-notes", "-jar", path.join(directory, "../upstream/oracle-db-mcp-java-toolkit/target/oracle-db-mcp-toolkit-1.0.0.jar")],
    env: toolkitEnvironment(loadConfig().database),
    // Upstream logs may include connection details. Never forward them to ChatGPT.
    stderr: "ignore",
  })
  : new StreamableHTTPClientTransport(new URL(upstreamUrl), {
  requestInit: { headers: token ? { Authorization: `Bearer ${token}` } : {} }
});
await upstream.connect(transport);

function createMcpServer() {
  const server = new McpServer({ name: "FinancialDB ChatGPT MCP App", version: "1.0.0" }, {
    instructions: "Use show-financialdb-notes to open the Oracle notes app. Read only the configured notes table. Adding a note requires the person to confirm in the app. Never request or expose database credentials or arbitrary SQL.",
  });
  registerAppTool(server, "show-financialdb-notes", {
    title: "Show FinancialDB notes",
    description: "Open a compact app to read recent notes and add a note to the FinancialDB notes table.",
    inputSchema: z.object({}),
    _meta: { ui: { resourceUri, visibility: ["model", "app"] } },
    annotations: { readOnlyHint: true, openWorldHint: false }
  }, async () => {
    const result = await safeCall("list-chatgpt-notes", {});
    if (result.isError) return { isError: true, content: [{ type: "text", text: "Could not read Oracle notes. Check the database connection and notes table." }] };
    return { ...result, content: [{ type: "text", text: "Opened the FinancialDB notes app." }] };
  });

  registerAppTool(server, "list-chatgpt-notes", {
    title: "Refresh FinancialDB notes", description: "Read the latest 50 notes.", inputSchema: z.object({}),
    _meta: { ui: { visibility: ["app"] } }, annotations: { readOnlyHint: true, openWorldHint: false }
  }, async () => safeCall("list-chatgpt-notes", {}));

  registerAppTool(server, "add-chatgpt-note", {
    title: "Add a FinancialDB note", description: "Insert one note after the user confirms in the app.",
    inputSchema: z.object({ noteText: z.string().trim().min(1).max(500) }),
    _meta: { ui: { visibility: ["app"] } }, annotations: { readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false }
  }, async ({ noteText }) => safeCall("add-chatgpt-note", { noteText }));

  registerAppResource(server, "FinancialDB Notes", resourceUri, { mimeType: RESOURCE_MIME_TYPE }, async () => ({
    contents: [{ uri: resourceUri, mimeType: RESOURCE_MIME_TYPE, text: bundle, _meta: { ui: { csp: { connectDomains: [], resourceDomains: [] } } } }]
  }));
  return server;
}

async function safeCall(name: string, args: Record<string, unknown>) {
  try {
    const result = await upstream.callTool({ name, arguments: args });
    if (!result.isError) return result;
  } catch { /* Do not return upstream exceptions or connection strings. */ }
  return { isError: true, content: [{ type: "text" as const, text: "Oracle notes operation failed. Check the Toolkit connection and table permissions." }] };
}

const app = express();
app.use((req, res, next) => {
  if (!['127.0.0.1', 'localhost', '::1'].includes(req.hostname)) return res.status(403).json({ error: 'Loopback host required' });
  if (req.headers.origin && ![`http://127.0.0.1:${port}`, `http://localhost:${port}`].includes(req.headers.origin)) {
    console.warn('Rejected MCP Origin');
    return res.status(403).json({ error: 'Origin not allowed' });
  }
  next();
});
if (process.env.CHATGPT_DEMO_HOST === '1') {
  app.get('/demo', (_req, res) => res.sendFile(path.join(directory, 'dist/demo-host.html')));
  app.get('/demo/source', async (req, res) => {
    const file = String(req.query.file);
    if (!['toolkit-tools.yaml', 'server.ts', 'src/app.ts', 'config.mjs'].includes(file)) return res.sendStatus(404);
    res.type('text/plain').send(await readFile(path.join(directory, file), 'utf8'));
  });
}
// Official dual-era handler: modern 2026-07-28 and legacy initialize clients.
const mcpHandler = toNodeHandler(createMcpHandler(createMcpServer));
app.all('/mcp', express.json(), (req, res) => void mcpHandler(req, res, req.body));
app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.use((req, res, next) => {
  if (req.path.startsWith("/.well-known/")) return res.status(404).json({ error: "No OAuth metadata is configured." });
  next();
});
const listener = app.listen(port, bind, () => console.log(`FinancialDB MCP App listening on ${bind}:${port}/mcp`));
for (const signal of ["SIGTERM", "SIGINT"] as const) process.once(signal, () => {
  listener.close();
  void upstream.close().finally(() => process.exit(0));
});
