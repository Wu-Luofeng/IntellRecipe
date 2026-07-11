# Level 2 Cleanup Script
# Cleans: vscode-cpptools cache, Trae cache, NVIDIA installer cache, JetBrains caches
$ErrorActionPreference = 'SilentlyContinue'

Write-Host "========================================"
Write-Host "  C Drive Cleanup - Level 2"
Write-Host "========================================"
Write-Host ""

$diskBefore = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
$freeBefore = [math]::Round($diskBefore.FreeSpace/1MB,1)
Write-Host "Free space before: $freeBefore MB"
Write-Host ""

$totalFreed = 0

# 1. Clean vscode-cpptools cache (6.58 GB)
Write-Host "[1/4] Cleaning vscode-cpptools cache..."
$cppToolsPath = 'C:\Users\ASUS\AppData\Local\Microsoft\vscode-cpptools'
$cppSize = (Get-ChildItem $cppToolsPath -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
$cppSizeGB = [math]::Round($cppSize/1GB,2)
Write-Host "  Size: $cppSizeGB GB"
Get-ChildItem $cppToolsPath -Directory -Force -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  Removing: $($_.Name)"
    Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
}
Get-ChildItem $cppToolsPath -File -Force -ErrorAction SilentlyContinue | ForEach-Object {
    Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
}
Write-Host "  Done. (Will auto-rebuild when C/C++ extension is used)"
$totalFreed += $cppSize
Write-Host ""

# 2. Clean Trae editor cache (keep config, clean caches)
Write-Host "[2/4] Cleaning Trae editor caches..."
$traeRoaming = 'C:\Users\ASUS\AppData\Roaming\Trae'
$traeCacheDirs = @(
    'Cache', 'Code Cache', 'CachedData', 'CachedExtensionVSIXs',
    'CachedProfilesData', 'GPUCache', 'DawnGraphiteCache',
    'DawnWebGPUCache', 'ShaderCache', 'GrShaderCache',
    'Service Worker', 'VideoDecodeStats', 'Crashpad'
)
$traeSize = 0
foreach ($dir in $traeCacheDirs) {
    $dirPath = Join-Path $traeRoaming $dir
    if (Test-Path $dirPath) {
        $dirSize = (Get-ChildItem $dirPath -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
        $traeSize += $dirSize
        Write-Host "  Cleaning: $dir ($([math]::Round($dirSize/1MB,1)) MB)"
        Remove-Item $dirPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$traeLogs = Join-Path $traeRoaming 'logs'
if (Test-Path $traeLogs) {
    $logSize = (Get-ChildItem $traeLogs -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    $traeSize += $logSize
    Write-Host "  Cleaning: logs ($([math]::Round($logSize/1MB,1)) MB)"
    Get-ChildItem $traeLogs -Force -ErrorAction SilentlyContinue | ForEach-Object {
        Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$traeWorktrees = 'C:\Users\ASUS\.trae\worktrees'
if (Test-Path $traeWorktrees) {
    $wtSize = (Get-ChildItem $traeWorktrees -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    $traeSize += $wtSize
    Write-Host "  Cleaning: .trae\worktrees ($([math]::Round($wtSize/1MB,1)) MB)"
    Remove-Item $traeWorktrees -Recurse -Force -ErrorAction SilentlyContinue
}
$traeSizeGB = [math]::Round($traeSize/1GB,2)
Write-Host "  Total Trae cache cleaned: $traeSizeGB GB"
Write-Host "  (User config and extensions preserved)"
$totalFreed += $traeSize
Write-Host ""

# 3. Clean NVIDIA driver installer cache
Write-Host "[3/4] Cleaning NVIDIA driver installer cache..."
$nvidiaPaths = @(
    'C:\ProgramData\NVIDIA Corporation\Downloader',
    'C:\ProgramData\NVIDIA\NV_Cache'
)
$nvidiaSize = 0
foreach ($path in $nvidiaPaths) {
    if (Test-Path $path) {
        $pathSize = (Get-ChildItem $path -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
        $nvidiaSize += $pathSize
        Write-Host "  Cleaning: $path ($([math]::Round($pathSize/1GB,2)) GB)"
        Remove-Item $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}
$nvidiaInstaller = 'C:\NVIDIA'
if (Test-Path $nvidiaInstaller) {
    $instSize = (Get-ChildItem $nvidiaInstaller -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    $nvidiaSize += $instSize
    Write-Host "  Cleaning: C:\NVIDIA ($([math]::Round($instSize/1GB,2)) GB)"
    Remove-Item $nvidiaInstaller -Recurse -Force -ErrorAction SilentlyContinue
}
$nvidiaSizeGB = [math]::Round($nvidiaSize/1GB,2)
Write-Host "  Total NVIDIA cache cleaned: $nvidiaSizeGB GB"
$totalFreed += $nvidiaSize
Write-Host ""

# 4. Clean JetBrains IDE caches
Write-Host "[4/4] Cleaning JetBrains IDE caches..."
$jbBase = 'C:\Users\ASUS\AppData\Local\JetBrains'
$jbSize = 0
if (Test-Path $jbBase) {
    Get-ChildItem $jbBase -Directory -Force -ErrorAction SilentlyContinue | ForEach-Object {
        $ideName = $_.Name
        $cacheDirs = @('caches', 'index', 'tmp', 'log')
        foreach ($cacheDir in $cacheDirs) {
            $cachePath = Join-Path $_.FullName $cacheDir
            if (Test-Path $cachePath) {
                $cSize = (Get-ChildItem $cachePath -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
                $jbSize += $cSize
                Write-Host "  Cleaning: $ideName\$cacheDir ($([math]::Round($cSize/1MB,1)) MB)"
                Get-ChildItem $cachePath -Force -ErrorAction SilentlyContinue | ForEach-Object {
                    Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
                }
            }
        }
    }
}
$jbSizeGB = [math]::Round($jbSize/1GB,2)
Write-Host "  Total JetBrains cache cleaned: $jbSizeGB GB"
Write-Host "  (IDE configs preserved, caches will rebuild on next launch)"
$totalFreed += $jbSize
Write-Host ""

# Summary
Write-Host "========================================"
Write-Host "  Level 2 Cleanup Summary"
Write-Host "========================================"
$totalFreedGB = [math]::Round($totalFreed/1GB,2)
Write-Host "Estimated freed: $totalFreedGB GB"
$diskAfter = Get-CimInstance Win32_LogicalDisk -Filter "DeviceID='C:'"
$freeAfter = [math]::Round($diskAfter.FreeSpace/1MB,1)
$freeAfterGB = [math]::Round($freeAfter/1024,2)
Write-Host "Free space after cleanup: $freeAfterGB GB ($freeAfter MB)"
$actualFreed = [math]::Round(($freeAfter - $freeBefore)/1024,2)
Write-Host "Actual freed: $actualFreed GB"
Write-Host ""
Write-Host "Done! Caches will auto-rebuild when applications are used."