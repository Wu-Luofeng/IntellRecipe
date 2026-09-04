# =====================================================================
# start-tunnel.ps1
# Open SSH local forward tunnels to the cloud middleware so that all
# services can stay on localhost (127.0.0.1) while DB/Redis/RabbitMQ/ES
# still live on the cloud server.
#
#   127.0.0.1:3307 -> <server>:3307  MySQL
#   127.0.0.1:6380 -> <server>:6379  Redis   (local 6379 is usually taken by the local Redis service)
#   127.0.0.1:5672 -> <server>:5672  RabbitMQ
#   127.0.0.1:9200 -> <server>:9200  Elasticsearch REST
#
# Prerequisites:
#   - passwordless SSH already configured (see deploy/SERVER_ACCESS.local.md)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy/local-dev/start-tunnel.ps1
#
# Keep this window open while you run the local services.
# =====================================================================

[CmdletBinding()]
param(
    [string]$Server = "129.204.203.184",
    [string]$User   = "ubuntu",
    # Forward list: each entry maps a LOCAL port to a REMOTE(cloud) port.
    # Redis uses 6380 -> 6379 because a local Windows "Redis" service usually
    # already owns 6379. Local services must therefore connect to REDIS_PORT=6380.
    [object[]]$Forwards = @(
        @{ Local = 3307; Remote = 3307 },  # MySQL
        @{ Local = 6380; Remote = 6379 },  # Redis (local 6380 -> cloud 6379)
        @{ Local = 5672; Remote = 5672 },  # RabbitMQ
        @{ Local = 9200; Remote = 9200 }   # Elasticsearch REST
        # Add more, e.g. @{ Local = 15672; Remote = 15672 } for RabbitMQ mgmt
    )
)

$ErrorActionPreference = "Stop"

$sshArgs = @(
    "-N",
    "-o", "ServerAliveInterval=30",
    "-o", "ServerAliveCountMax=3",
    "-o", "ConnectTimeout=10"
    # NOTE: do NOT add ExitOnForwardFailure=yes here - if a local port is already
    # taken (e.g. a local Redis on 6379) ssh would exit and kill ALL tunnels.
)

foreach ($f in $Forwards) {
    $sshArgs += "-L"
    $sshArgs += "127.0.0.1:$($f.Local):127.0.0.1:$($f.Remote)"
    Write-Host ("  forward 127.0.0.1:{0} -> {1}:{2}" -f $f.Local, $Server, $f.Remote)
}

$sshArgs += "${User}@${Server}"

Write-Host ""
Write-Host "SSH tunnels established. Press Ctrl+C to stop. Keep this window open."
Write-Host ""

# Foreground tunnel. If a local port is already taken, ssh will exit with an error.
& ssh @sshArgs
