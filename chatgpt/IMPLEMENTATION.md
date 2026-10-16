# ChatGPT Oracle MCP App implementation

Updated October 16, 2026.

## Execution path

The ChatGPT-facing Node server delegates the two configured notes operations to the vendored Oracle Database MCP Java Toolkit. The Windows launcher uses an SDK-managed stdio child; the previous authenticated HTTPS upstream mode remains available for separately managed Toolkit installations. The notes app does not invoke the AI Fullstack Java framework's approval gate or simulator backends.

`show-financialdb-notes` is model-visible and read-only. `list-chatgpt-notes` and `add-chatgpt-note` are app-only in compliant hosts. The app resource registration uses the SDK's name, URI argument order. The insert accepts a trimmed string of 1–500 characters and uses the Toolkit's parameter binding. The read formats the Oracle timestamp as ISO-style text because raw JDBC timestamp objects failed JSON serialization once the table contained a row.

`config.mjs` reads root `.env`, optional `chatgpt/.env`, then process overrides. OCI is the default profile; GCP is selectable. Explicit DB variables take precedence. The file parser does not execute shell syntax. The Oracle child receives only a small OS environment allowlist plus the selected database settings. Tunnel runtime keys are excluded from file parsing and child inheritance.

The UI checkbox is a user interaction guard. It is not a backend authorization or durable approval mechanism. An arbitrary authorized MCP client can call the insert directly. This sample must stay private; production use needs authenticated callers, server-enforced approvals, durable audit and transaction retry/idempotency policy. Host and Origin checks restrict the local listener; there is no app-level OAuth implementation.

## Verified on this workstation

- The read-only Java probe connected using the root OCI environment profile. Both configured wallet directories were absent, but the OCI JDBC descriptor connected successfully without them.
- The existing `CHATGPT_NOTES` table required no schema changes.
- One exact note was explicitly approved by the user and inserted through the real MCP App: `Conference demo: Oracle MCP App connected successfully.` No second insert was performed.
- A read after restarting the app and its Toolkit child returned one row, demonstrating persistence in Oracle.
- The MCP smoke test verified initialization, all three tools, the live notes read, and the bundled UI resource.
- The app build, literal credential parsing tests, and full repository `./build_all.sh` reactor build passed.
- The final app uses the split MCP v2 SDK: server/client 2.3.1, Node adapter 2.1.1, and MCP Apps bridge 2.0.3. `createMcpHandler` serves modern `2026-07-28` traffic and legacy `initialize` clients on one endpoint. The Oracle child uses the legacy client path. Both modes passed discovery, app-only refresh and resource reads. The build-only `vite-plugin-singlefile` dependency chain still reports a braces advisory; npm currently offers a major downgrade as its proposed fix. No forced downgrade was applied.

## ChatGPT connection checkpoint

An existing FinancialDB tunnel was found in OpenAI Platform with organization and ChatGPT workspace associations. Its identifier and associations are intentionally omitted from committed notes. No runtime-key profile was available on this Windows machine. The user was given `connect-tunnel.ps1`, which prompts locally for an existing key and stores it only in the tunnel process environment.

FinancialDB Notes is installed and connected in ChatGPT. A new conversation opened the dashboard through the model-visible tool, displayed the persisted conference note, and successfully refreshed it using the app-only read tool. The user approved only one insert earlier; this verification performed no additional writes.

The account's existing `financialdb-chatgpt-tunnel-runtime` key was confirmed active, expiring November 14, 2026; its secret is masked in the key list and was not retrieved. The user created a new key with All permissions and explicitly stored it in the ignored root `.env` as `CHATGPT_KEY_FOR_MCPAPP_PLUGIN`. `tunnel.mjs` supports this user-selected local configuration, passes the key only in the tunnel child's environment, and excludes all database credentials from that process. The normal database configuration parser excludes the custom key. No key value was printed or committed. The key's permissions were not changed by the agent.

On October 16, the tunnel started successfully and `/readyz` returned `ready`. Initial launcher startup exposed a CLI requirement that `--log.level` must be accompanied by `--log.format`; both launchers now provide `struct-text`. A nonfatal OAuth discovery warning was observed for this sample's no-OAuth endpoint. ChatGPT discovery and connection succeeded. Interactive refresh initially failed because the v1 SDK rejected ChatGPT's `2026-07-28` protocol header; the official MCP v2 migration resolved it without rewriting or stripping protocol headers.

## Conference artifacts

`video/scenes.json` is the narration and scene source. Captures show actual app and repository source; existing note text is masked in the recording host except for conference demo notes. `video/build.ps1` creates local speech, an H.264/AAC MP4, SRT/WebVTT and a poster. `video/verify.mjs` decodes the entire movie and checks stream metadata, caption formatting, blog references and credential values in text artifacts. Inspect each rendered scene for readability and secret exposure before using it publicly.

The conference MP4 has burned captions. A separate `oracle-chatgpt-walkthrough-web.mp4` uses the WebVTT track in the article, avoiding duplicate captions. The final scene captures the actual ChatGPT dashboard after a successful Oracle refresh, with unrelated chat history and account controls cropped out. The current exact duration and full decode results are in the generated `video/verification.json`. All scenes must be reviewed after regeneration.

The installation revision adds authentic Add-menu, reopened setup-form, and installed-plugin captures. The form uses a placeholder tunnel ID and was cancelled without creating a duplicate. Narration explains Create, Connect and Try in chat; the original Connect consent dialog was not recorded. Exact duration, scene count and decode results are in video/verification.json.

Finished MP4s, poster, captions, narration, timing, verification report and reviewed captures are committed so another checkout can show the walkthrough without database access. Intermediate renders, local tool downloads, credentials and build output remain ignored. Open blog.html or serve chatgpt/ with a static HTTP server for browser caption support.
