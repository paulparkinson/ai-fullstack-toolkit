import { Client } from '@modelcontextprotocol/client';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/client';
import { AppBridge, PostMessageTransport } from '@modelcontextprotocol/ext-apps/app-bridge';

const evidence = document.querySelector<HTMLElement>('#evidence')!;
const frame = document.querySelector<HTMLIFrameElement>('#app')!;
const appPane = document.querySelector<HTMLElement>('#app-pane')!;
const sourcePane = document.querySelector<HTMLElement>('#source-pane')!;
const client = new Client({ name: 'conference-local-host', version: '1.0.0' });
await client.connect(new StreamableHTTPClientTransport(new URL('/mcp', location.href)));
const tools = await client.listTools();
evidence.textContent = `MCP initialized · ${tools.tools.length} tools discovered · no database credentials in the browser`;
let bridge: AppBridge | undefined;

// Recording-only privacy filter. The production ChatGPT resource is unchanged.
function recordingResult(result: any) {
  const rows = result.structuredContent?.rows;
  if (!Array.isArray(rows)) return result;
  return { ...result, content: [{ type: 'text', text: 'Oracle read completed; existing note text hidden in recording.' }], structuredContent: {
    ...result.structuredContent,
    rows: rows.map((row: any) => ({ ...row, NOTE_TEXT: String(row.NOTE_TEXT ?? row.note_text ?? '').startsWith('Conference demo:') ? row.NOTE_TEXT ?? row.note_text : '[Existing note hidden for recording]', note_text: undefined })),
  } };
}

document.querySelector<HTMLButtonElement>('#open')!.onclick = async () => {
  sourcePane.hidden = true; appPane.hidden = false;
  const result = await client.callTool({ name: 'show-financialdb-notes', arguments: {} });
  if (result.isError) { evidence.textContent = 'Oracle read failed. Run npm run doctor.'; return; }
  const resource = await client.readResource({ uri: 'ui://financialdb/chatgpt-notes-v1' });
  if (bridge) await bridge.close();
  bridge = new AppBridge(null, { name: 'Local conference host', version: '1.0.0' }, { serverTools: {} });
  bridge.oncalltool = async params => {
    if (!['list-chatgpt-notes', 'add-chatgpt-note'].includes(params.name)) throw new Error('Tool not allowed');
    const response = await client.callTool(params);
    evidence.textContent = `${params.name} · ${response.isError ? 'failed' : 'completed through Oracle Toolkit'}`;
    return recordingResult(response);
  };
  bridge.oninitialized = async () => {
    await bridge!.sendToolInput({ arguments: {} });
    await bridge!.sendToolResult(recordingResult(result));
    evidence.textContent = `show-financialdb-notes · live Oracle read · ${result.structuredContent?.rowCount ?? '?'} rows`;
  };
  await bridge.connect(new PostMessageTransport(frame.contentWindow!, frame.contentWindow!));
  frame.srcdoc = resource.contents[0].text as string;
};
for (const button of document.querySelectorAll<HTMLButtonElement>('[data-source]')) button.onclick = async () => {
  appPane.hidden = true; sourcePane.hidden = false;
  const name = button.dataset.source!;
  document.querySelector('#filename')!.textContent = `chatgpt/${name}`;
  document.querySelector('#source')!.textContent = await (await fetch(`/demo/source?file=${encodeURIComponent(name)}`)).text();
};
