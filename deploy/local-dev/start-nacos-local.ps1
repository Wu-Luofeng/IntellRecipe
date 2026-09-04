# =====================================================================
# start-nacos-local.ps1
# Run an ISOLATED Nacos (standalone, embedded Derby) on this machine.
# Local services register here so they never pollute the cloud Nacos
# instance (no "shadow instances" mixed into the production registry).
#
# Nacos is downloaded once into %USERPROFILE%\.local-dev\ (~100 MB).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy/local-dev/start-nacos-local.ps1
#
# Console: http://127.0.0.1:8848/nacos  (nacos / nacos)
# Stop:    run bin\shutdown.cmd or kill the java process.
# =====================================================================

[CmdletBinding()]
param(
    [string]$NacosVersion = "2.2.0",
    [string]$BaseDir      = (Join-Path $env:USERPROFILE ".local-dev")
)

$ErrorActionPreference = "Stop"

$installDir = Join-Path $BaseDir "nacos"
$zipPath    = Join-Path $BaseDir "nacos-server-$NacosVersion.zip"
$downloadUrl = "https://github.com/alibaba/nacos/releases/download/$NacosVersion/nacos-server-$NacosVersion.zip"

# Nacos startup.cmd needs JAVA_HOME. Fall back to resolving javac from PATH.
if (-not $env:JAVA_HOME) {
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if ($javac) {
        $env:JAVA_HOME = Split-Path (Split-Path $javac.Source -Parent) -Parent
        Write-Host "JAVA_HOME not set, auto-detected: $env:JAVA_HOME"
    }
}
if (-not $env:JAVA_HOME) {
    Write-Error "JAVA_HOME is not set and javac was not found on PATH. Install JDK 8+ first."
    exit 1
}

New-Item -ItemType Directory -Force -Path $BaseDir | Out-Null

if (-not (Test-Path (Join-Path $installDir "bin"))) {
    if (Test-Path $installDir) {
        # Cleanup a half-extracted folder, keep the zip cache.
        Remove-Item $installDir -Recurse -Force
    }
    if (-not (Test-Path $zipPath)) {
        Write-Host "Downloading Nacos $NacosVersion ..."
        Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
    }
    Write-Host "Extracting $zipPath -> $BaseDir ..."
    Expand-Archive -Path $zipPath -DestinationPath $BaseDir -Force
}

$startup = Join-Path $installDir "bin\startup.cmd"
if (-not (Test-Path $startup)) {
    throw "Nacos startup.cmd not found at $startup"
}

Write-Host ""
Write-Host "Starting local Nacos (standalone) ..."
Write-Host "  Console : http://127.0.0.1:8848/nacos  (nacos / nacos)"
Write-Host "  gRPC    : 127.0.0.1:9848 (used by Nacos 2.x clients)"
Write-Host "  Stop    : $installDir\bin\shutdown.cmd"
Write-Host ""

Push-Location (Split-Path $startup -Parent)
& $startup -m standalone
Pop-Location
