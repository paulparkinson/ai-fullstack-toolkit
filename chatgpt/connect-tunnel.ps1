param([Parameter(Mandatory=$true)][string]$TunnelId)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path $PSScriptRoot -Parent
$localEnv = Join-Path $workspace '.env'
if ((Test-Path $localEnv) -and (Select-String -Path $localEnv -Pattern '^\s*CHATGPT_KEY_FOR_MCPAPP_PLUGIN\s*=' -Quiet)) {
  $bundledNode = Join-Path $workspace '.runtime/tools/node.exe'
  $node = if (Test-Path $bundledNode) { $bundledNode } else { (Get-Command node).Source }
  & $node (Join-Path $PSScriptRoot 'tunnel.mjs') --tunnel-id $TunnelId
  exit $LASTEXITCODE
}
$bundledClient = Join-Path $workspace '.runtime/tools/tunnel/tunnel-client.exe'
$client = if (Test-Path $bundledClient) { $bundledClient } else { (Get-Command tunnel-client).Source }
$env:CONTROL_PLANE_TUNNEL_ID = $TunnelId
$env:MCP_SERVER_URL = 'http://127.0.0.1:3001/mcp'
if ((Invoke-RestMethod 'http://127.0.0.1:3001/health').status -ne 'ok') { throw 'Start the local app first' }
$secret = Read-Host 'Existing OpenAI runtime key with Tunnels Read and Use (hidden)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try {
  $env:CONTROL_PLANE_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
  & $client doctor --explain
  if ($LASTEXITCODE -ne 0) { throw 'Tunnel preflight failed' }
  & $client run --health.listen-addr 127.0.0.1:8766 --log.level=warn --log.format=struct-text
} finally {
  Remove-Item Env:CONTROL_PLANE_API_KEY -ErrorAction SilentlyContinue
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
  $secret.Dispose()
}
