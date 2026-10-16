import { defineConfig } from "vite";
import { viteSingleFile } from "vite-plugin-singlefile";

export default defineConfig(({ mode }) => ({
  plugins: [viteSingleFile()],
  build: { outDir: "dist", emptyOutDir: mode !== 'demo', rollupOptions: { input: mode === 'demo' ? 'demo-host.html' : 'mcp-app.html' } }
}));
