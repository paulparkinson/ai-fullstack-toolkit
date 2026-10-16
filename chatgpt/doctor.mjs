import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { existsSync } from 'node:fs';
import { directory, loadConfig, toolkitEnvironment } from './config.mjs';

try {
  const config = loadConfig();
  console.log(`PROFILE=${config.profile}\nWALLET_AVAILABLE=${config.walletAvailable}`);
  const jar = path.join(directory, '../upstream/oracle-db-mcp-java-toolkit/target/oracle-db-mcp-toolkit-1.0.0.jar');
  if (!existsSync(jar)) throw new Error('Build upstream/oracle-db-mcp-java-toolkit first');
  const result = spawnSync('java', ['--class-path', jar, path.join(directory, 'database/CheckDatabase.java')], {
    env: toolkitEnvironment(config.database), encoding: 'utf8', timeout: 60000,
  });
  // JDBC failures are reduced to numeric codes by the Java probe.
  console.log(result.stdout || 'Database probe could not complete');
  process.exitCode = result.status === 0 ? 0 : 1;
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
