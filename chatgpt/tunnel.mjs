import { readFileSync, existsSync } from 'node:fs';
import { spawn } from 'node:child_process';
import { createInterface } from 'node:readline';
import path from 'node:path';
import { directory, parseEnv, toolkitEnvironment } from './config.mjs';

const option = process.argv.indexOf('--tunnel-id');
const tunnelId = option >= 0 ? process.argv[option + 1] : process.env.CONTROL_PLANE_TUNNEL_ID;
if (!tunnelId?.match(/^tunnel_[A-Za-z0-9]+$/)) throw new Error('Supply --tunnel-id tunnel_...');
const read = file => existsSync(file) ? parseEnv(readFileSync(file, 'utf8'), { includeTunnelKey: true }) : {};
const config = { ...read(path.join(directory, '../.env')), ...read(path.join(directory, '.env')) };
const key = process.env.CONTROL_PLANE_API_KEY || process.env.CHATGPT_KEY_FOR_MCPAPP_PLUGIN || config.CHATGPT_KEY_FOR_MCPAPP_PLUGIN;
if (!key || !key.startsWith('sk-') || /\s/.test(key)) throw new Error('A valid CHATGPT_KEY_FOR_MCPAPP_PLUGIN is required in the local .env or process environment');
const bundled = path.join(directory, '../.runtime/tools/tunnel/tunnel-client.exe');
const executable = process.env.TUNNEL_CLIENT || (existsSync(bundled) ? bundled : 'tunnel-client');
// Reuse only OS process settings. No Oracle credentials enter the tunnel child.
const env = { ...toolkitEnvironment({}), CONTROL_PLANE_API_KEY: key, CONTROL_PLANE_TUNNEL_ID: tunnelId, MCP_SERVER_URL: 'http://127.0.0.1:3001/mcp' };
const child = spawn(executable, ['run', '--health.listen-addr', '127.0.0.1:8766', '--log.level=warn', '--log.format=struct-text'], { env, stdio: ['ignore', 'pipe', 'pipe'] });
// Redact the exact credential even if a future CLI error unexpectedly echoes it.
for (const stream of [child.stdout, child.stderr]) createInterface({ input: stream }).on('line', line => console.log(line.replaceAll(key, '[REDACTED]')));
child.on('error', () => { console.error('Tunnel client could not start'); process.exitCode = 1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
for (const signal of ['SIGINT', 'SIGTERM']) process.once(signal, () => child.kill());
console.log('Tunnel client starting with the configured local runtime key.');
