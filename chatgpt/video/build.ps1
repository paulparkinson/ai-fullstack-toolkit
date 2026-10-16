param([string]$FFmpeg, [string]$FFprobe, [string]$Voice = 'Microsoft Zira Desktop')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
if (!$FFmpeg) { $FFmpeg = Join-Path $workspace '.runtime/media/node_modules/@ffmpeg-installer/win32-x64/ffmpeg.exe' }
if (!$FFprobe) { $FFprobe = Join-Path $workspace '.runtime/media/node_modules/@ffprobe-installer/win32-x64/ffprobe.exe' }
if (!(Test-Path $FFmpeg) -or !(Test-Path $FFprobe)) { throw 'Supply -FFmpeg and -FFprobe paths to installed media tools' }
Add-Type -AssemblyName System.Speech
$speech = New-Object System.Speech.Synthesis.SpeechSynthesizer
$speech.SelectVoice($Voice)
$speech.Rate = 0
$scenes = Get-Content (Join-Path $PSScriptRoot 'scenes.json') -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Force (Join-Path $PSScriptRoot 'render') | Out-Null
function Stamp([double]$seconds, [string]$separator = ',') {
  $time = [TimeSpan]::FromMilliseconds([Math]::Round($seconds * 1000))
  return '{0:00}:{1:00}:{2:00}{3}{4:000}' -f [Math]::Floor($time.TotalHours),$time.Minutes,$time.Seconds,$separator,$time.Milliseconds
}
function Wrap([string]$text) {
  $lines = @(); $line = ''
  foreach ($word in $text.Split(' ')) {
    if ($line.Length -gt 0 -and ($line.Length + 1 + $word.Length) -gt 42) { $lines += $line; $line = '' }
    $line = if ($line) { "$line $word" } else { $word }
  }
  if ($line) { $lines += $line }; return $lines
}
$offset = 0.0; $captionIndex = 1; $allCaptions = @(); $narration = @(); $segments = @(); $timing = @()
Push-Location $PSScriptRoot
try {
  for ($index = 0; $index -lt $scenes.Count; $index++) {
    $scene = $scenes[$index]; $id = '{0:00}' -f ($index + 1)
    if (!$scene.image -or !$scene.narration -or !$scene.seconds) { throw "Invalid scene definition at index $index" }
    $image = Join-Path 'captures' $scene.image
    if (!(Test-Path $image)) { throw "Missing authentic browser capture: $image" }
    $wav = "render/scene-$id.wav"
    $speech.SetOutputToWaveFile((Join-Path $PSScriptRoot $wav)); $speech.Speak($scene.narration); $speech.SetOutputToNull()
    $audioDuration = [double](& $FFprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $wav)
    $duration = [Math]::Max([double]$scene.seconds, [Math]::Ceiling($audioDuration + 1))
    $lines = @(Wrap $scene.narration); $chunks = @()
    for ($lineIndex = 0; $lineIndex -lt $lines.Count; $lineIndex += 2) {
      $last = [Math]::Min($lineIndex + 1, $lines.Count - 1)
      $chunks += ($lines[$lineIndex..$last] -join "`n")
    }
    $local = @()
    for ($chunkIndex = 0; $chunkIndex -lt $chunks.Count; $chunkIndex++) {
      $start = $chunkIndex * $audioDuration / $chunks.Count
      $end = ($chunkIndex + 1) * $audioDuration / $chunks.Count
      $local += "$($chunkIndex + 1)`n$(Stamp $start) --> $(Stamp $end)`n$($chunks[$chunkIndex])`n"
      $allCaptions += "$captionIndex`n$(Stamp ($offset + $start)) --> $(Stamp ($offset + $end))`n$($chunks[$chunkIndex])`n"
      $captionIndex++
    }
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot "render/scene-$id.srt"),($local -join "`n"),[Text.UTF8Encoding]::new($false))
    $filter = "scale=1920:940:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:0:color=0x0c1825"
    & $FFmpeg -y -hide_banner -loglevel error -loop 1 -framerate 24 -i $image -i $wav -vf $filter -t $duration -c:v libx264 -preset fast -crf 19 -pix_fmt yuv420p -c:a aac -b:a 160k -af apad -movflags +faststart "render/scene-$id.mp4"
    if ($LASTEXITCODE -ne 0) { throw "Encoding failed: scene $id" }
    $segments += "file 'scene-$id.mp4'"
    $narration += "$($scene.title)`n$($scene.narration)`n"
    $timing += @{ title=$scene.title; start=$offset; duration=$duration; audioDuration=$audioDuration; image=$scene.image }
    $offset += $duration
  }
  [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'render/concat.txt'),($segments -join "`n"),[Text.UTF8Encoding]::new($false))
  & $FFmpeg -y -hide_banner -loglevel error -f concat -safe 0 -i render/concat.txt -c copy -movflags +faststart oracle-chatgpt-walkthrough-web.mp4
  if ($LASTEXITCODE -ne 0) { throw 'Final concatenation failed' }
  [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'captions.srt'),($allCaptions -join "`n"),[Text.UTF8Encoding]::new($false))
  $vtt = "WEBVTT`n`n" + (($allCaptions -join "`n") -replace '(\d{2}:\d{2}:\d{2}),(\d{3})','$1.$2')
  [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'captions.vtt'),$vtt,[Text.UTF8Encoding]::new($false))
  [IO.File]::WriteAllText((Join-Path $PSScriptRoot 'narration.txt'),($narration -join "`n"),[Text.UTF8Encoding]::new($false))
  $timing | ConvertTo-Json -Depth 4 | Set-Content -Encoding UTF8 timing.json
  & $FFmpeg -y -hide_banner -loglevel error -i oracle-chatgpt-walkthrough-web.mp4 -vf "subtitles=captions.srt`:force_style='FontName=Arial,FontSize=12,Outline=1,MarginV=10'" -c:v libx264 -preset fast -crf 19 -pix_fmt yuv420p -c:a copy -movflags +faststart oracle-chatgpt-walkthrough.mp4
  if ($LASTEXITCODE -ne 0) { throw 'Conference caption burn failed' }
  & $FFmpeg -y -hide_banner -loglevel error -i oracle-chatgpt-walkthrough.mp4 -frames:v 1 poster.png
  Write-Output "Video complete: $offset seconds, 1920x1080, narrated H.264/AAC, SRT and WebVTT."
} finally { $speech.Dispose(); Pop-Location }
