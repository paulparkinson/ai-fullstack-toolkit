import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests', timeout: 30000, workers: 1,
  use: { baseURL: 'http://127.0.0.1:8082', viewport: {width:1280,height:960},
    launchOptions: {args:['--enable-unsafe-swiftshader']}, screenshot:'only-on-failure' },
  webServer: { command:'java -jar ../target/gcp-workflow-simulator-0.1.0-SNAPSHOT.jar',
    url:'http://127.0.0.1:8082', reuseExistingServer:false, timeout:30000 }
});
