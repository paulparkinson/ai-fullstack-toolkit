import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const directory = path.dirname(fileURLToPath(import.meta.url));

// Parse values literally: never source/evaluate a credential file as code.
export function parseEnv(text, { includeTunnelKey = false } = {}) {
  const result = {};
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim() || line.trimStart().startsWith('#')) continue;
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$/);
    if (!match) throw new Error('Invalid environment file entry');
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
    if (match[1] === 'CONTROL_PLANE_API_KEY') continue;
    if (match[1] === 'CHATGPT_KEY_FOR_MCPAPP_PLUGIN' && !includeTunnelKey) continue;
    result[match[1]] = value;
  }
  return result;
}

export function loadConfig(overrides = process.env) {
  const read = file => existsSync(file) ? parseEnv(readFileSync(file, 'utf8')) : {};
  const values = { ...read(path.join(directory, '..', '.env')), ...read(path.join(directory, '.env')), ...overrides };
  const profile = (values.CHATGPT_DB_PROFILE || 'OCI').toUpperCase();
  if (!['OCI', 'GCP'].includes(profile)) throw new Error('CHATGPT_DB_PROFILE must be OCI or GCP');
  const database = {
    DB_URL: values.DB_URL || values[`${profile}_DB_URL`],
    DB_USERNAME: values.DB_USERNAME || values[`${profile}_DB_USERNAME`],
    DB_PASSWORD: values.DB_PASSWORD || values[`${profile}_DB_PASSWORD`],
  };
  for (const [key, value] of Object.entries(database)) if (!value) throw new Error(`Missing ${key} for ${profile} profile`);
  const wallet = values.TNS_ADMIN || values[`${profile}_TNS_ADMIN`];
  // A full TCPS descriptor may not need a wallet. Do not silently replace its URL.
  if (wallet && existsSync(wallet) && !database.DB_URL.includes('TNS_ADMIN=')) database.DB_URL += `${database.DB_URL.includes('?') ? '&' : '?'}TNS_ADMIN=${wallet.replaceAll('\\', '/')}`;
  return { values, database, profile, walletAvailable: !!wallet && existsSync(wallet) };
}

export function toolkitEnvironment(database, source = process.env) {
  // Only the database child gets DB values; no tunnel/API key is inherited.
  const env = {};
  for (const key of ['PATH', 'Path', 'JAVA_HOME', 'SystemRoot', 'WINDIR', 'TEMP', 'TMP', 'HOME', 'USERPROFILE']) if (source[key]) env[key] = source[key];
  return { ...env, ...database };
}
