# Safe Cleanup Script - Level 1 Only
# Only cleans items that are 100% safe to remove
$ErrorActionPreference = 'SilentlyContinue'

Write-Host "========================================"
Write-Host "  C Drive Safe Cleanup - Level 1"
Write-Host "========================================"
Write-Host ""

# Get free space before cleanup
$diskBefore = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
$freeBefore = [math]::Round($diskBefore.FreeSpace/1MB,1)
Write-Host "Free space before cleanup: $freeBefore MB"
Write-Host ""

$totalFreed = 0

# 1. Clean npm cache
Write-Host "[1/7] Cleaning npm cache..."
$npmSize = (Get-ChildItem 'C:\Users\ASUS\AppData\Local\npm-cache' -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$npmSizeMB = [math]::Round($npmSize/1MB,1)
Write-Host "  Size: $npmSizeMB MB"
try {
    npm cache clean --force 2>$null
    Write-Host "  Done."
    $totalFreed += $npmSizeMB
} catch {
    Write-Host "  Skipped (npm not available or cache in use)"
}
Write-Host ""

# 2. Clean pip cache
Write-Host "[2/7] Cleaning pip cache..."
$pipSize = (Get-ChildItem 'C:\Users\ASUS\AppData\Local\pip\cache' -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$pipSizeMB = [math]::Round($pipSize/1MB,1)
Write-Host "  Size: $pipSizeMB MB"
try {
    pip cache purge 2>$null
    Write-Host "  Done."
    $totalFreed += $pipSizeMB
} catch {
    Write-Host "  Skipped"
}
Write-Host ""

# 3. Clean Windows Update Download cache
Write-Host "[3/7] Cleaning Windows Update download cache..."
$wuSize = (Get-ChildItem 'C:\Windows\SoftwareDistribution\Download' -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$wuSizeMB = [math]::Round($wuSize/1MB,1)
Write-Host "  Size: $wuSizeMB MB"
try {
    # Stop wuauserv service first
    Stop-Service -Name wuauserv -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Remove-Item 'C:\Windows\SoftwareDistribution\Download\*' -Recurse -Force -ErrorAction SilentlyContinue
    Start-Service -Name wuauserv -ErrorAction SilentlyContinue
    Write-Host "  Done."
    $totalFreed += $wuSizeMB
} catch {
    Write-Host "  Skipped (may need admin rights)"
}
Write-Host ""

# 4. Clean User Temp folder
Write-Host "[4/7] Cleaning User Temp folder..."
$tempSize = (Get-ChildItem "$env:TEMP" -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$tempSizeMB = [math]::Round($tempSize/1MB,1)
Write-Host "  Size: $tempSizeMB MB"
Get-ChildItem "$env:TEMP" -Force -ErrorAction SilentlyContinue | ForEach-Object {
    Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "  Done (some files in use may be skipped)."
$totalFreed += $tempSizeMB
Write-Host ""

# 5. Empty Recycle Bin
Write-Host "[5/7] Emptying Recycle Bin..."
$shell = New-Object -ComObject Shell.Application
$recycleBin = $shell.Namespace(0xA)
$recycleSize = 0
foreach ($item in $recycleBin.Items()) {
    $recycleSize += $item.Size
}
$recycleSizeMB = [math]::Round($recycleSize/1MB,1)
Write-Host "  Size: $recycleSizeMB MB"
Clear-RecycleBin -Force -ErrorAction SilentlyContinue
Write-Host "  Done."
$totalFreed += $recycleSizeMB
Write-Host ""

# 6. Clean $WINDOWS.~BT (Windows upgrade temp)
Write-Host "[6/7] Cleaning Windows upgrade temp files..."
$btSize = (Get-ChildItem 'C:\$WINDOWS.~BT' -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$btSizeMB = [math]::Round($btSize/1MB,1)
Write-Host "  Size: $btSizeMB MB"
Remove-Item 'C:\$WINDOWS.~BT' -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "  Done (may need admin rights for some files)."
$totalFreed += $btSizeMB
Write-Host ""

# 7. Clean Thumbnail cache
Write-Host "[7/7] Cleaning thumbnail cache..."
$thumbSize = (Get-ChildItem 'C:\Users\ASUS\AppData\Local\Microsoft\Windows\Explorer' -Filter "thumbcache_*" -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$thumbSizeMB = [math]::Round($thumbSize/1MB,1)
Write-Host "  Size: $thumbSizeMB MB"
Get-ChildItem 'C:\Users\ASUS\AppData\Local\Microsoft\Windows\Explorer' -Filter "thumbcache_*" -Force -ErrorAction SilentlyContinue | ForEach-Object {
    Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
}
Write-Host "  Done."
$totalFreed += $thumbSizeMB
Write-Host ""

# Summary
Write-Host "========================================"
Write-Host "  Cleanup Summary"
Write-Host "========================================"
Write-Host "Estimated freed: $([math]::Round($totalFreed/1024,2)) GB"
$diskAfter = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
$freeAfter = [math]::Round($diskAfter.FreeSpace/1MB,1)
Write-Host "Free space after cleanup: $freeAfter MB"
Write-Host "Actual freed: $([math]::Round(($freeAfter - $freeBefore)/1024,2)) GB"
Write-Host ""
Write-Host "Note: For more space, see C-drive-cleanup-report.md for Level 2-4 options."