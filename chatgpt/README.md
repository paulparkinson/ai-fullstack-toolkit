# FinancialDB ChatGPT MCP App

This MCP App reads and writes a dedicated `CHATGPT_NOTES` table in the connected Oracle account through the Oracle Database MCP Java Toolkit. Use either `FINANCIAL` or `ADMIN`. The model can open the app, but only the app exposes the insert action; the user confirms before a write. No database credentials enter the browser bundle.

See [blog.html](blog.html) for setup and ChatGPT connection steps. Configure the Oracle Toolkit with [toolkit-tools.yaml](toolkit-tools.yaml) and the ignored `.env` values copied from `.env.example`. Run [database/setup.sql](database/setup.sql) once as the same account configured in `DB_USERNAME` before enabling the insert tool.

The sample expects Node.js 22.12+, Java/Maven for the Toolkit, an OCI FinancialDB wallet, and an HTTPS endpoint for ChatGPT. Start the Toolkit with the `chatgpt-notes` toolset, then run `./run.sh`. The app server binds to loopback by default; use an authenticated HTTPS endpoint when connecting ChatGPT. For local Toolkit TLS, configure Node to trust its certificate using `NODE_EXTRA_CA_CERTS`.
