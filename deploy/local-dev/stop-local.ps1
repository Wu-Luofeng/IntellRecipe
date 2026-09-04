# =====================================================================
# stop-local.ps1
# Stop everything started for local end-to-end testing:
#   1. the six Spring Boot services
#   2. the local isolated Nacos
#   3. the SSH tunnel
# PIDs are read from the files written by the start commands/scripts.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy/local-dev/stop-local.ps1
# =====================================================================

[CmdletBinding()]
param([string]$Root = "")

# NOTE: $PSScriptRoot is empty inside param() defaults; resolve here.
if ([string]::IsNullOrEmpty($Root)) {
    $Root = Split-Path $PSScriptRoot -Parent
}

$ErrorActionPreference = "SilentlyContinue"

function Stop-Tree([int]$Pid, [string]$What) {
    if ($Pid -gt 0) {
        Write-Host ("Stopping {0} (pid {1}, whole process tree) ..." -f $What, $Pid)
        taskkill /PID $Pid /T /F 2>$null | Out-Null
    }
}

# 1) Spring Boot services (service=pid lines from .local-services.txt)
$svcFile = Join-Path $Root '.local-services.txt'
if (Test-Path $svcFile) {
    Get-Content $svcFile | ForEach-Object {
        $parts = $_ -split '='
        if ($parts.Count -eq 2) {
            Stop-Tree -Pid ([int]$parts[1]) -What $parts[0]
        }
    }
} else {
    Write-Host 'No .local-services.txt found - services may have been started manually.'
}

# 2) Local Nacos
$np = Join-Path $Root '.local-nacos.pid'
if (Test-Path $np) { Stop-Tree -Pid ([int](Get-Content $np)) -What 'Nacos' }

# 3) SSH tunnel
$tp = Join-Path $Root '.local-tunnel.pid'
if (Test-Path $tp) { Stop-Tree -Pid ([int](Get-Content $tp)) -What 'SSH tunnel' }

Write-Host ''
Write-Host 'Done. Check no listeners remain:'
Write-Host '  netstat -ano | findstr LISTENING | findstr "8081 8082 8083 8084 8085 10010 8848 3307 6380 5672 9200"'
