$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendDir = Join-Path $root "waternet"
$frontendDir = Join-Path $root "vue-waternet"

$mavenCandidates = Get-ChildItem -Path (Join-Path $env:USERPROFILE ".m2\wrapper\dists\apache-maven-3.9.11-bin") -Filter "mvn.cmd" -Recurse -ErrorAction SilentlyContinue |
    Sort-Object FullName

if ($mavenCandidates.Count -gt 0) {
    $mavenCmd = $mavenCandidates[0].FullName
} else {
    $mavenCmd = "mvn.cmd"
}

$backendCommand = "Set-Location '$backendDir'; & '$mavenCmd' spring-boot:run"
$frontendCommand = "Set-Location '$frontendDir'; npm.cmd run dev -- --host 127.0.0.1"

Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-NoExit", "-Command", $backendCommand -WindowStyle Normal
Start-Process -FilePath "powershell.exe" -ArgumentList "-NoProfile", "-ExecutionPolicy", "Bypass", "-NoExit", "-Command", $frontendCommand -WindowStyle Normal

Write-Host "Backend:  http://localhost:8080/api/network/overview"
Write-Host "Frontend: http://127.0.0.1:5173/"
Write-Host "Keep both opened PowerShell windows running while using the app."
