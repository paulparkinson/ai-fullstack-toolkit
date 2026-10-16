import assert from 'node:assert/strict';
import { Client } from '@modelcontextprotocol/client';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/client';

const modern = process.argv.includes('--modern');
const client = new Client({ name: 'conference-preflight', version: '1.0.0' }, modern ? { versionNegotiation: { mode: { pin: '2026-07-28' } } } : {});
try {
  await client.connect(new StreamableHTTPClientTransport(new URL('http://127.0.0.1:3001/mcp')));
  const { tools } = await client.listTools();
  assert.deepEqual(tools.map(tool => tool.name).sort(), ['add-chatgpt-note', 'list-chatgpt-notes', 'show-financialdb-notes']);
  const show = tools.find(tool => tool.name === 'show-financialdb-notes');
  assert.equal(show.annotations.readOnlyHint, true);
  const result = await client.callTool({ name: 'show-financialdb-notes', arguments: {} });
  assert.ok(!result.isError, 'Oracle read failed');
  assert.ok(Array.isArray(result.structuredContent?.rows));
  const resource = await client.readResource({ uri: 'ui://financialdb/chatgpt-notes-v1' });
  assert.ok(resource.contents[0].text.includes('FinancialDB Notes'));
  assert.equal(client.getProtocolEra(), modern ? 'modern' : 'legacy');
  const refreshed = await client.callTool({ name: 'list-chatgpt-notes', arguments: {} });
  assert.ok(!refreshed.isError && Array.isArray(refreshed.structuredContent?.rows), 'App-only read failed');
  console.log(JSON.stringify({ transport: 'streamable-http', era: client.getProtocolEra(), tools: tools.map(tool => tool.name), databaseRead: 'passed', appRefresh: 'passed', resource: 'passed', rowCount: result.structuredContent.rows.length }, null, 2));
} finally { await client.close(); }
