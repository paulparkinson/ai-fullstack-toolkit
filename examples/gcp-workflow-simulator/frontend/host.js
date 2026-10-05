import './style.css';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { AppBridge, PostMessageTransport } from '@modelcontextprotocol/ext-apps/app-bridge';

const $ = id => document.getElementById(id);
const client = new Client({name:'fullstack-simulator-host',version:'0.1.0'});
let bridge;
const trace = value => { $('trace').textContent = JSON.stringify(value, null, 2); };
function text(tag, value) { const el=document.createElement(tag);el.textContent=value;return el; }
async function a2a(data) {
  const request={jsonrpc:'2.0',id:crypto.randomUUID(),method:'message/send',params:{message:{kind:'message',role:'user',messageId:crypto.randomUUID(),parts:[{kind:'data',data}]}}};
  const response=await fetch('/a2a',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(request)});
  const rpc=await response.json();trace({transport:'A2A',request,response:rpc});
  if(rpc.error)throw new Error(rpc.error.message);
  return rpc.result.parts;
}
function renderA2ui(messages) {
  for(const msg of messages) {
    if(!msg.surfaceUpdate)continue;
    const {surfaceId,components}=msg.surfaceUpdate;
    const componentsById=new Map(components.map(c=>[c.id,c]));
    const section=document.createElement('section');section.className='review';
    function render(id) {
      const c=componentsById.get(id)?.component;
      if(c?.Text)return text('p',c.Text.text.literalString);
      if(c?.Column){const div=document.createElement('div');for(const child of c.Column.children.explicitList)div.append(render(child));return div;}
      if(c?.Button){const action=c.Button.action;const button=text('button',componentsById.get(c.Button.child).component.Text.text.literalString);
        button.onclick=async()=>{button.disabled=true;try{
          const context=Object.fromEntries(action.context.map(v=>[v.key,v.value.literalString]));
          const parts=await a2a({userAction:{name:action.name,surfaceId,sourceComponentId:id,timestamp:new Date().toISOString(),context}});
          section.replaceChildren(text('p',parts[0].data.data.status));$('status').textContent='Explicit approval recorded one simulated write.';
        }catch(e){section.append(text('p',e.message));}};return button;}
      throw new Error('Unsupported A2UI component');
    }
    const begin=messages.find(m=>m.beginRendering?.surfaceId===surfaceId);
    section.append(render(begin.beginRendering.root));$('result').append(section);
  }
}
async function renderApp(tool,result,args) {
  const uri=tool._meta.ui.resourceUri;
  const resource=await client.readResource({uri});
  const frame=document.createElement('iframe');frame.title='Interactive '+result.structuredContent.data.kind;
  frame.sandbox='allow-scripts'; // Opaque origin: no access to host DOM, cookies or same-origin storage.
  $('result').append(frame);
  bridge=new AppBridge(null,{name:'simulator-host',version:'0.1.0'},{});
  const ready=new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>reject(new Error('MCP App initialization timed out')),10000);
    bridge.oninitialized=async()=>{try{await bridge.sendToolInput({arguments:args});await bridge.sendToolResult(result);resolve();}
      catch(e){reject(e);}finally{clearTimeout(timeout);}};
  });
  await bridge.connect(new PostMessageTransport(frame.contentWindow,frame.contentWindow));
  // All libraries are bundled. No external network or same-origin API access from the app.
  frame.srcdoc=resource.contents[0].text;
  await ready;
}
function table(rows) {
  const t=document.createElement('table');const head=document.createElement('tr');
  for(const label of ['SKU','Product','Warehouse','Risk (0–1)'])head.append(text('th',label));t.append(head);
  for(const row of rows){const tr=document.createElement('tr');for(const key of ['sku','product','warehouse','risk'])tr.append(text('td',row[key]));t.append(tr);}
  $('result').append(t);
}
async function run(operation,sku=$('sku').value) {
  document.querySelectorAll('nav button,#prompt-form button').forEach(b=>b.disabled=true);
  try {
    if(bridge){await bridge.close();bridge=null;}$('result').replaceChildren();$('status').textContent='Running '+operation+'…';
    if(operation==='suggest-inventory-transfers'){
      const parts=await a2a({operation,arguments:{minimumRisk:70,limit:3}});
      renderA2ui(parts.filter(p=>p.metadata?.mimeType==='application/json+a2ui').map(p=>p.data));
      $('status').textContent='A2A → A2UI review. Zero writes until explicit approval.';
    }else{
      const args=operation==='list-stockout-risk'?{}:{sku};
      const result=await client.callTool({name:operation,arguments:args});trace({transport:'MCP',operation,arguments:args,result});
      if(result.isError)throw new Error(result.content[0].text);
      const tool=(await client.listTools()).tools.find(t=>t.name===operation);
      if(tool._meta?.ui)await renderApp(tool,result,args);else table(result.structuredContent.data.rows);
      $('status').textContent='SIMULATED managed-agent read · '+result.structuredContent.evidence.upstreamRequestId;
    }
  }catch(e){$('status').textContent=e.message;}
  finally{document.querySelectorAll('nav button,#prompt-form button').forEach(b=>b.disabled=false);}
}
document.querySelectorAll('[data-operation]').forEach(b=>b.onclick=()=>run(b.dataset.operation));
$('prompt-form').onsubmit=e=>{e.preventDefault();const prompt=$('prompt').value;
  const sku=prompt.match(/SKU-[A-Z0-9-]+/i)?.[0].toUpperCase()??$('sku').value;
  if(/\bgraph\b/i.test(prompt))run('show-supply-chain-graph',sku);
  else if(/\b(map|spatial)\b/i.test(prompt))run('show-spatial-hotspots',sku);
  else if(/\b(suggest|review)\b.*\btransfer/i.test(prompt))run('suggest-inventory-transfers');
  else if(/\blist\b.*\b(stockout|stock outages)/i.test(prompt))run('list-stockout-risk');
  else $('status').textContent='Unsupported simulator prompt. Use one of the four demo steps.';
};
try {await client.connect(new StreamableHTTPClientTransport(new URL('/mcp',location.href)));$('status').textContent='Connected. Start with a plain risk list; graph and map are opt-in.';}
catch(e){$('status').textContent='Connection failed: '+e.message;}
