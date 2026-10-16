import test from 'node:test';
import assert from 'node:assert/strict';
import { parseEnv, toolkitEnvironment, loadConfig } from './config.mjs';

test('credential file is literal and excludes tunnel runtime keys', () => {
  assert.deepEqual(parseEnv('A="x&y=$(command)"\r\n# comment\r\nB=hello=world\nCONTROL_PLANE_API_KEY=secret'), { A: 'x&y=$(command)', B: 'hello=world' });
  assert.throws(() => parseEnv('BAD ENTRY=value'));
});
test('Oracle child receives DB credentials but no cloud or tunnel keys', () => {
  assert.deepEqual(toolkitEnvironment({ DB_PASSWORD: 'test-only' }, { PATH: 'java', CONTROL_PLANE_API_KEY: 'excluded', OCI_DB_PASSWORD: 'excluded' }), { PATH: 'java', DB_PASSWORD: 'test-only' });
});
test('the user-configured tunnel key is available only to the tunnel reader', () => {
  const input = 'CHATGPT_KEY_FOR_MCPAPP_PLUGIN=test-only\nDB_USERNAME=demo';
  assert.deepEqual(parseEnv(input), { DB_USERNAME: 'demo' });
  assert.deepEqual(parseEnv(input, { includeTunnelKey: true }), { CHATGPT_KEY_FOR_MCPAPP_PLUGIN: 'test-only', DB_USERNAME: 'demo' });
});
test('explicit DB settings override the selected cloud profile', () => {
  const { database, profile } = loadConfig({ CHATGPT_DB_PROFILE: 'GCP', DB_URL: 'jdbc:oracle:thin:@example?TNS_ADMIN=unused', DB_USERNAME: 'demo-only', DB_PASSWORD: 'demo-only', GCP_DB_PASSWORD: 'wrong-profile-value' });
  assert.equal(profile, 'GCP');
  assert.deepEqual(database, { DB_URL: 'jdbc:oracle:thin:@example?TNS_ADMIN=unused', DB_USERNAME: 'demo-only', DB_PASSWORD: 'demo-only' });
  assert.throws(() => loadConfig({ CHATGPT_DB_PROFILE: 'unexpected' }), /must be OCI or GCP/);
});
