import { App } from "@modelcontextprotocol/ext-apps";

const app = new App({ name: "FinancialDB Notes", version: "1.0.0" });
const status = document.querySelector<HTMLElement>("#status")!;
const notes = document.querySelector<HTMLElement>("#notes")!;
const input = document.querySelector<HTMLTextAreaElement>("#note")!;
const save = document.querySelector<HTMLButtonElement>("#save")!;
const refresh = document.querySelector<HTMLButtonElement>("#refresh")!;
const confirmWrite = document.querySelector<HTMLInputElement>("#confirm-write")!;

app.ontoolresult = result => {
  const rows = (result.structuredContent as { rows?: Array<Record<string, unknown>> } | undefined)?.rows ?? [];
  render(rows);
};

function render(rows: Array<Record<string, unknown>>) {
  notes.replaceChildren();
  if (!rows.length) {
    const empty = document.createElement("p"); empty.textContent = "No notes yet. Add the first one above."; notes.append(empty); return;
  }
  for (const row of rows) {
    const card = document.createElement("article"); card.className = "note";
    const body = document.createElement("div"); body.textContent = String(row.NOTE_TEXT ?? row.note_text ?? "");
    const date = document.createElement("div"); date.className = "date"; date.textContent = String(row.CREATED_AT ?? row.created_at ?? "");
    card.append(body, date); notes.append(card);
  }
}

async function callTool(name: string, args: Record<string, unknown> = {}) {
  const result = await app.callServerTool({ name, arguments: args });
  if (result.isError) throw new Error(result.content?.map(item => item.type === "text" ? item.text : "").join(" ") || "Toolkit request failed");
  return result;
}

refresh.onclick = async () => {
  refresh.disabled = true; status.textContent = "Loading from FinancialDB…";
  try { const result = await callTool("list-chatgpt-notes"); render((result.structuredContent as { rows?: Array<Record<string, unknown>> })?.rows ?? []); status.textContent = "Loaded from Oracle FinancialDB."; }
  catch { status.textContent = "Could not load notes. Check the Toolkit connection."; }
  finally { refresh.disabled = false; }
};

save.onclick = async () => {
  const noteText = input.value.trim();
  if (!noteText || noteText.length > 500) { status.textContent = "Enter a note from 1 to 500 characters."; return; }
  if (!confirmWrite.checked) { status.textContent = "Confirm the write using the checkbox first."; return; }
  save.disabled = true; status.textContent = "Writing note to FinancialDB…";
  try { await callTool("add-chatgpt-note", { noteText }); input.value = ""; confirmWrite.checked = false; status.textContent = "Note added."; refresh.click(); }
  catch { status.textContent = "Write failed. Check Toolkit permissions and the database table."; }
  finally { save.disabled = false; }
};

app.connect().then(() => { status.textContent = "Connected to the ChatGPT MCP App host. Use Refresh notes to read."; }).catch(() => { status.textContent = "MCP App host bridge could not initialize."; });
