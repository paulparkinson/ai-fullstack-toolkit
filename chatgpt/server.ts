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

const server = new McpServer({ name: "FinancialDB ChatGPT MCP App", version: "1.0.0" });
registerAppTool(server, "show-financialdb-notes", {
  title: "Show FinancialDB notes",
  description: "Open a compact app to read recent notes and add one to the FinancialDB notes table.",
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

const app = express();
const http = new StreamableHTTPServerTransport({ sessionIdGenerator: undefined });
await server.connect(http);
app.post("/mcp", express.json(), async (req, res) => {
  await http.handleRequest(req, res, req.body);
});
app.get("/health", (_req, res) => res.json({ status: "ok" }));
app.listen(port, bind, () => console.log(`FinancialDB MCP App listening on ${bind}:${port}/mcp`));
