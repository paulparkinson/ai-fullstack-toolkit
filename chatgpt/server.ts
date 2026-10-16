import { readFile } from "node:fs/promises";
import path from "node:path";
import express from "express";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";
import { registerAppResource, registerAppTool, RESOURCE_MIME_TYPE } from "@modelcontextprotocol/ext-apps/server";
import { z } from "zod";

const upstreamUrl = process.env.ORACLE_MCP_URL ?? "https://127.0.0.1:45450/mcp";
const token = process.env.ORACLE_MCP_AUTH_TOKEN;
const port = Number(process.env.PORT ?? "3001");
const bind = process.env.MCP_BIND_HOST ?? "127.0.0.1";
const resourceUri = "ui://financialdb/chatgpt-notes-v1";
const bundle = await readFile(path.join(import.meta.dirname, "dist", "mcp-app.html"), "utf8");
const upstream = new Client({ name: "financialdb-chatgpt-app", version: "1.0.0" });
const transport = new StreamableHTTPClientTransport(new URL(upstreamUrl), {
  requestInit: { headers: token ? { Authorization: `Bearer ${token}` } : {} }
});
await upstream.connect(transport);

function createMcpServer() {
  const server = new McpServer({ name: "FinancialDB ChatGPT MCP App", version: "1.0.0" });
  registerAppTool(server, "show-financialdb-notes", {
    title: "Show FinancialDB notes",
    description: "Open a compact app to read recent notes and add a note to the FinancialDB notes table.",
    inputSchema: {},
    _meta: { ui: { resourceUri, visibility: ["model", "app"] } },
    annotations: { readOnlyHint: true, openWorldHint: false }
  }, async () => {
    const result = await upstream.callTool({ name: "list-chatgpt-notes", arguments: {} });
    return { ...result, content: [{ type: "text", text: "Opened the FinancialDB notes app." }] };
  });

  registerAppTool(server, "list-chatgpt-notes", {
    title: "Refresh FinancialDB notes", description: "Read the latest 50 notes.", inputSchema: {},
    _meta: { ui: { visibility: ["app"] } }, annotations: { readOnlyHint: true, openWorldHint: false }
  }, async () => upstream.callTool({ name: "list-chatgpt-notes", arguments: {} }));

  registerAppTool(server, "add-chatgpt-note", {
    title: "Add a FinancialDB note", description: "Insert one note after the user confirms in the app.",
    inputSchema: { noteText: z.string().trim().min(1).max(500) },
    _meta: { ui: { visibility: ["app"] } }, annotations: { destructiveHint: false, idempotentHint: false, openWorldHint: false }
  }, async ({ noteText }) => upstream.callTool({ name: "add-chatgpt-note", arguments: { noteText } }));

  registerAppResource(server, resourceUri, "FinancialDB Notes", { mimeType: RESOURCE_MIME_TYPE }, async () => ({
    contents: [{ uri: resourceUri, mimeType: RESOURCE_MIME_TYPE, text: bundle, _meta: { ui: { csp: { connectDomains: [], resourceDomains: [] } } } }]
  }));
  return server;
}

const app = express();
app.post("/mcp", express.json(), async (req, res) => {
  const server = createMcpServer();
  const http = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
  http.onerror = (error) => console.error("MCP transport error:", error.name, error.message);
  try {
    await server.connect(http);
    await http.handleRequest(req, res, req.body);
    res.on("close", () => {
      void http.close();
      void server.close();
    });
  } catch (error) {
    console.error("MCP request failed:", error instanceof Error ? `${error.name}: ${error.message}` : "unknown error");
    if (!res.headersSent) res.status(500).json({ error: "MCP request failed" });
    void http.close();
    void server.close();
  }
});
app.get("/mcp", (_req, res) => res.status(405).json({ error: "Use POST for Streamable HTTP MCP." }));
app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.use((req, res, next) => {
  if (req.path.startsWith("/.well-known/")) return res.status(404).json({ error: "No OAuth metadata is configured." });
  next();
});
app.listen(port, bind, () => console.log(`FinancialDB MCP App listening on ${bind}:${port}/mcp`));
