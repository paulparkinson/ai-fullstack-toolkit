# FinancialDB ChatGPT MCP App

This directory contains a reusable MCP App that lets ChatGPT read the latest 50 rows from `CHATGPT_NOTES` and insert a note after a person confirms the write in the app. The app calls two narrowly scoped tools in the Oracle Database MCP Java Toolkit; it never receives database credentials or a JDBC connection.

## Reuse on another computer

Use the checked-in code; no reimplementation is needed. A fresh computer does need its own local database wallet/configuration, TLS keystore, OpenAI tunnel runtime key, and compatible ChatGPT workspace. None of those machine credentials are committed. Do not copy this computer's `.env`, wallet, certificates, or runtime API key.

The directory uses the Toolkit checkout in `../upstream/oracle-db-mcp-java-toolkit`. Install a JDK 17+, Maven 3.9+, Node.js 22.12+, and npm. Install `tunnel-client` from the download link in [OpenAI Platform tunnel settings](https://platform.openai.com/settings/organization/tunnels) or the [latest official release](https://github.com/openai/tunnel-client/releases/latest). Check the current command syntax with `tunnel-client help quickstart`; this launcher uses the `CONTROL_PLANE_TUNNEL_ID`, `CONTROL_PLANE_API_KEY`, and `MCP_SERVER_URL` environment variables supported by the CLI.

### 1. Configure the database

From the repository root:

```sh
cp chatgpt/.env.example chatgpt/.env
```

Edit `chatgpt/.env` on the new computer. Set `DB_URL` to the JDBC Thin wallet alias and that machine's wallet path, then set `DB_USERNAME` to `FINANCIAL` or `ADMIN` and `DB_PASSWORD` to that account's password. Keep `.env` and the wallet local; Git ignores them. `ORACLE_MCP_URL` should remain `https://127.0.0.1:45450/mcp`, and `ORACLE_MCP_AUTH_TOKEN` should be a new local development token.

Run `chatgpt/database/setup.sql` once in the target schema as the account selected by `DB_USERNAME`. It creates that account's `CHATGPT_NOTES` table.

### 2. Create a local Toolkit certificate

The Toolkit serves HTTPS on loopback port 45450. Create a local PKCS#12 keystore and export its certificate so Node can trust it. `keytool` prompts for the keystore password; use a local development password and keep both files out of Git.

```sh
mkdir -p chatgpt/tls
keytool -genkeypair \
  -alias financialdb-chatgpt \
  -keyalg RSA -keysize 3072 -validity 365 \
  -storetype PKCS12 \
  -keystore chatgpt/tls/financialdb-chatgpt.p12 \
  -dname "CN=localhost" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
keytool -exportcert -rfc \
  -alias financialdb-chatgpt \
  -keystore chatgpt/tls/financialdb-chatgpt.p12 \
  -file chatgpt/tls/financialdb-chatgpt.crt
```

### 3. Build and run the Oracle Toolkit

In Terminal 1, build the checked-in Toolkit:

```sh
cd upstream/oracle-db-mcp-java-toolkit
mvn clean package
cd ../..
```

Start the Toolkit from the repository root. It reads the database values from `chatgpt/.env`, loads only the `chatgpt-notes` toolset, authenticates the Node app with the matching token, and listens on `127.0.0.1` only.

```sh
cd chatgpt
SCRIPT_DIR="$PWD"
source ./load-env.sh
export ORACLE_DB_TOOLKIT_AUTH_TOKEN="$ORACLE_MCP_AUTH_TOKEN"
read -r -s -p "Local Toolkit keystore password: " TLS_KEYSTORE_PASSWORD; printf '\n'
java \
  -Dtransport=http \
  -Dhttps.port=45450 \
  -Dhttps.address=127.0.0.1 \
  -DcertificatePath="$PWD/tls/financialdb-chatgpt.p12" \
  -DcertificatePassword="$TLS_KEYSTORE_PASSWORD" \
  -DenableAuthentication=true \
  -DconfigFile="$PWD/toolkit-tools.yaml" \
  -Dtools=chatgpt-notes \
  -jar ../upstream/oracle-db-mcp-java-toolkit/target/oracle-db-mcp-toolkit-1.0.0.jar
```

Keep this terminal open. The keystore password is passed to the local JVM because the Toolkit accepts it as a system property; use a development-only password and do not capture it in a recording or terminal log.

### 4. Create or select a tunnel for the target ChatGPT workspace

In OpenAI Platform, create a tunnel in the Platform organization associated with the ChatGPT workspace you will use, or reuse a tunnel only after confirming that it is associated with that workspace. A tunnel associated only with an Enterprise workspace or organization may not appear in a Personal workspace. The account creating the tunnel needs Tunnels Read and Manage; the runtime key needs only Tunnels Read and Use. Creating a tunnel and granting ChatGPT access to custom MCP servers are separate permissions.

Create a runtime API key for `tunnel-client` with Tunnels Read and Use. Do not use an admin key. This key is not the Toolkit token in `.env`; it authenticates the tunnel process to OpenAI. The runtime key and tunnel ID are specific to the Platform organization/workspace setup and are not in this repository.

### 5. Run the MCP App and private tunnel

In Terminal 2, from the repository root:

```sh
cd chatgpt
SCRIPT_DIR="$PWD"
source ./load-env.sh
export NODE_EXTRA_CA_CERTS="$PWD/tls/financialdb-chatgpt.crt"
export CONTROL_PLANE_TUNNEL_ID="tunnel_..."
read -r -s -p "OpenAI tunnel runtime key: " CONTROL_PLANE_API_KEY; printf '\n'
export CONTROL_PLANE_API_KEY
./run_with_ssl.sh
```

The launcher builds the web bundle, starts the app on `127.0.0.1:3001`, waits for `/health`, and starts `tunnel-client` in the foreground. Keep it running for ChatGPT discovery and tool calls. The app port and tunnel health/admin UI remain bound to loopback; the tunnel makes outbound HTTPS connections and does not expose an inbound public listener.

You can check local readiness from another terminal:

```sh
curl -fsS http://127.0.0.1:3001/health
curl -fsS http://127.0.0.1:8766/readyz
```

### 6. Add it to ChatGPT and confirm the read path

In the target ChatGPT workspace, open Plugins, select **+ → Add custom MCP server**, choose **Tunnel**, enter or select the tunnel ID, and configure authentication as required. Review the tool list, then start a new chat with the connector and ask it to show the FinancialDB notes. The `show-financialdb-notes` and `list-chatgpt-notes` tools are read-only; the `add-chatgpt-note` tool is available in the app UI and inserts only after the user confirms.

If ChatGPT says custom apps are not allowed, the workspace policy must be changed by its administrator; changing computers or selecting the Personal plugin-directory tab does not bypass that workspace restriction. If the tunnel is missing, check that it is associated with the target ChatGPT workspace and that the connector operator has Tunnels Read and Use.

For current tunnel setup and permission details, see the [OpenAI Secure MCP Tunnel guide](https://developers.openai.com/api/docs/guides/secure-mcp-tunnels).

## Files

- `toolkit-tools.yaml` — read and insert SQL tools for the Toolkit.
- `server.ts` — ChatGPT-facing MCP server and bridge to the Toolkit.
- `src/app.ts` — compact MCP App UI and explicit write confirmation.
- `database/setup.sql` — creates the isolated notes table.
- `run.sh` — local app run without an OpenAI tunnel.
- `run_with_ssl.sh` — loopback app plus Secure MCP Tunnel.
- `load-env.sh` — loads local `.env` values literally and refuses to load the tunnel runtime key from that file.
