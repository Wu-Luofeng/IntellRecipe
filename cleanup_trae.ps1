$ErrorActionPreference = 'SilentlyContinue'

Write-Host "===== Kill Trae and Clean Remaining ====="
Write-Host ""

# Kill Trae processes
Write-Host "Killing Trae processes..."
Get-Process | Where-Object { $_.Name -like '*Trae*' -or $_.Name -like '*trae*' } | ForEach-Object {
    Write-Host "  Killing: $($_.Name) (PID: $($_.Id))"
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Seconds 2
Write-Host "Done."
Write-Host ""

# Remove remaining Trae folder
$traePath = 'C:\Users\ASUS\AppData\Roaming\Trae'
if (Test-Path $traePath) {
    $beforeSize = (Get-ChildItem $traePath -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    Write-Host "Removing remaining Trae data: $([math]::Round($beforeSize/1MB,1)) MB"
    Remove-Item $traePath -Recurse -Force -ErrorAction SilentlyContinue
    if (Test-Path $traePath) {
        $afterSize = (Get-ChildItem $traePath -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
        Write-Host "  Remaining: $([math]::Round($afterSize/1MB,1)) MB (some files still locked)"
    } else {
        Write-Host "  Fully removed!"
    }
} else {
    Write-Host "Trae folder already gone!"
}

Write-Host ""
$disk = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
$freeGB = [math]::Round($disk.FreeSpace/1GB,2)
Write-Host "Current C: free space: $freeGB GB"
