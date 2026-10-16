param([ValidateSet('OCI', 'GCP')][string]$Profile = 'OCI', [switch]$Tunnel)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
  $env:CHATGPT_DB_PROFILE = $Profile
  $env:ORACLE_MCP_TRANSPORT = 'stdio'
  $env:MCP_BIND_HOST = '127.0.0.1'
  npm ci
  if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
  npm run build
  if ($LASTEXITCODE -ne 0) { throw 'App build failed' }
  if (!$Tunnel) {
    npm run serve
  } else {
    if (!$env:CONTROL_PLANE_TUNNEL_ID -or !$env:CONTROL_PLANE_API_KEY) { throw 'Set tunnel ID and runtime key in this process environment first.' }
    $nodeCommand = (Get-Command node).Source
    $appProcess = Start-Process -FilePath $nodeCommand -ArgumentList @('--import', 'tsx', 'server.ts') -WindowStyle Hidden -PassThru
    try {
      $appPort = if ($env:PORT) { $env:PORT } else { '3001' }
      $ready = $false
      for ($attempt = 0; $attempt -lt 60; $attempt++) {
        if ($appProcess.HasExited) { throw 'App exited before readiness' }
        try { $ready = (Invoke-RestMethod "http://127.0.0.1:$appPort/health").status -eq 'ok' } catch { }
        if ($ready) { break }
        Start-Sleep -Seconds 1
      }
      if (!$ready) { throw 'App did not become ready' }
      $env:MCP_SERVER_URL = "http://127.0.0.1:$appPort/mcp"
      tunnel-client run --health.listen-addr 127.0.0.1:8766 --log.level=info --log.format=struct-text
    } finally {
      # Terminate only the process tree this launcher created, including Java.
      if (!$appProcess.HasExited) { & taskkill.exe /PID $appProcess.Id /T /F | Out-Null }
    }
  }
} finally { Pop-Location }
