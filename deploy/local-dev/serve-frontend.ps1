# =====================================================================
# serve-frontend.ps1  (OPTIONAL)
# A tiny local web server that mimics what cloud Nginx does, so you can
# open the Vue pages without Docker/Nginx:
#
#   /           -> static files from docker/nginx/html
#   /uploads/*  -> %USERPROFILE%\IntellRecipe\uploads
#   /api/*      -> proxy to http://127.0.0.1:10010/*  (local Gateway)
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy/local-dev/serve-frontend.ps1
#   then open http://127.0.0.1:8080
#
# If binding fails with Access Denied, run once (as Administrator):
#   netsh http add urlacl url=http://127.0.0.1:8080/ user=Everyone
# =====================================================================

[CmdletBinding()]
param(
    [int]   $Port       = 8080,
    [string]$WebRoot    = "",
    [string]$UploadRoot = (Join-Path $env:USERPROFILE "IntellRecipe\uploads"),
    [string]$Gateway    = "http://127.0.0.1:10010"
)

$ErrorActionPreference = "Stop"

# NOTE: $PSScriptRoot is NOT available inside the param() default-value
# expressions, so resolve the repo-relative paths here instead.
if ([string]::IsNullOrEmpty($WebRoot)) {
    $WebRoot = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) "docker\nginx\html"
}

if (-not (Test-Path $WebRoot)) { throw "WebRoot not found: $WebRoot" }

$MimeMap = @{
    ".html" = "text/html; charset=utf-8";  ".htm" = "text/html; charset=utf-8"
    ".js"   = "application/javascript";    ".css" = "text/css"
    ".png"  = "image/png"; ".jpg" = "image/jpeg"; ".jpeg" = "image/jpeg"
    ".gif"  = "image/gif";  ".svg" = "image/svg+xml"; ".ico" = "image/x-icon"
    ".json" = "application/json"; ".txt" = "text/plain; charset=utf-8"; ".map" = "application/json"
    ".woff" = "font/woff"; ".woff2" = "font/woff2"; ".ttf" = "font/ttf"
}

function Send-TextResponse {
    param($Context, [int]$Status, [string]$Body, [string]$ContentType = "text/plain; charset=utf-8")
    $Context.Response.StatusCode = $Status
    $Context.Response.ContentType = $ContentType
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
    $Context.Response.OutputStream.Write($bytes, 0, $bytes.Length)
    $Context.Response.Close()
}

function Send-FileResponse {
    param($Context, [string]$FilePath)
    $ext = [System.IO.Path]::GetExtension($FilePath).ToLower()
    $mime = if ($MimeMap.ContainsKey($ext)) { $MimeMap[$ext] } else { "application/octet-stream" }
    $Context.Response.StatusCode = 200
    $Context.Response.ContentType = $mime
    $stream = [System.IO.File]::OpenRead($FilePath)
    try { $stream.CopyTo($Context.Response.OutputStream) }
    finally { $stream.Dispose(); $Context.Response.Close() }
}


function Invoke-Request {
    param($Context)
    try {
        $path = $Context.Request.Url.AbsolutePath
        $method = $Context.Request.HttpMethod

        if ($path.StartsWith("/api/")) {
            # Same rule as nginx: proxy_pass http://127.0.0.1:10010/  (strip /api)
            $target = $Gateway + $path.Substring(4)
            if ($Context.Request.Url.Query) { $target += $Context.Request.Url.Query }

            $req = [System.Net.HttpWebRequest]::Create($target)
            $req.Method = $method
            $req.Timeout = 120000
            try { $req.ContentType = $Context.Request.ContentType } catch { }
            foreach ($h in $Context.Request.Headers.AllKeys) {
                if ($h -in @("Host", "Content-Length", "Connection", "Accept-Encoding")) { continue }
                try { $req.Headers[$h] = $Context.Request.Headers[$h] } catch { }
            }
            if ($method -in @('POST', 'PUT', 'PATCH') -and $Context.Request.ContentLength64 -gt 0) {
                $in = $Context.Request.InputStream
                $out = $req.GetRequestStream()
                $in.CopyTo($out)
                $out.Dispose()
            }
            try {
                $resp = $req.GetResponse()
                try {
                    $Context.Response.StatusCode = [int]$resp.StatusCode
                    $Context.Response.ContentType = $resp.ContentType
                    foreach ($hn in $resp.Headers.AllKeys) {
                        if ($hn -in @("Transfer-Encoding", "Content-Length", "Connection", "Keep-Alive")) { continue }
                        try { $Context.Response.Headers[$hn] = $resp.Headers[$hn] } catch { }
                    }
                    $resp.GetResponseStream().CopyTo($Context.Response.OutputStream)
                } finally { $resp.Close() }
            } catch [System.Net.WebException] {
                $er = $_.Exception.Response
                if ($er) {
                    $Context.Response.StatusCode = [int]$er.StatusCode
                    try { $er.GetResponseStream().CopyTo($Context.Response.OutputStream) } catch { }
                } else {
                    Send-TextResponse -Context $Context -Status 502 -Body ("Gateway unreachable: " + $_.Exception.Message)
                }
            } finally {
                try { $Context.Response.Close() } catch { }
            }
            return
        }

        if ($path.StartsWith("/uploads/")) {
            $rel = $path.Substring("/uploads/".Length).Replace("/", "\")
            $full = [System.IO.Path]::GetFullPath((Join-Path $UploadRoot $rel))
            $rootFull = [System.IO.Path]::GetFullPath($UploadRoot)
            if ($full.StartsWith($rootFull) -and (Test-Path -LiteralPath $full -PathType Leaf)) {
                Send-FileResponse -Context $Context -FilePath $full
            } else {
                Send-TextResponse -Context $Context -Status 404 -Body "Not found"
            }
            return
        }

        $file = $path.Replace("/", "\")
        if ($file -eq "\") { $file = "\index.html" }
        $full = [System.IO.Path]::GetFullPath((Join-Path $WebRoot $file.TrimStart("\")))
        $rootFull = [System.IO.Path]::GetFullPath($WebRoot)
        if ($full.StartsWith($rootFull) -and (Test-Path -LiteralPath $full -PathType Leaf)) {
            Send-FileResponse -Context $Context -FilePath $full
        } else {
            Send-TextResponse -Context $Context -Status 404 -Body "Not found: $path"
        }
    } catch {
        try { Send-TextResponse -Context $Context -Status 500 -Body ("Error: " + $_.Exception.Message) } catch { }
    }
}

$listener = New-Object System.Net.HttpListener
$prefix = "http://127.0.0.1:$Port/"
$listener.Prefixes.Add($prefix)

try {
    $listener.Start()
} catch {
    Write-Host "Trying to grant URL ACL automatically ..."
    netsh http add urlacl url=$prefix user=Everyone 2>$null | Out-Null
    try {
        $listener.Start()
    } catch {
        Write-Host ""
        Write-Error "Cannot bind $prefix . Run once as Administrator:"
        Write-Error "  netsh http add urlacl url=$prefix user=Everyone"
        throw
    }
}

Write-Host "Serving  $WebRoot"
Write-Host "Uploads  $UploadRoot"
Write-Host "API      $Gateway   (/api/* -> $Gateway/*)"
Write-Host "Open     http://127.0.0.1:$Port   (Ctrl+C to stop)"
Write-Host ""

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    Invoke-Request -Context $ctx
}
