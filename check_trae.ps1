$ErrorActionPreference = 'SilentlyContinue'
$traePath = 'C:\Users\ASUS\AppData\Roaming\Trae'
if (Test-Path $traePath) {
    Write-Host "Remaining contents in Trae folder:"
    Get-ChildItem $traePath -Force -ErrorAction SilentlyContinue | Select-Object Name
    $s = (Get-ChildItem $traePath -Recurse -Force -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum
    $sMB = [math]::Round($s/1MB,1)
    Write-Host "Remaining size: $sMB MB"
} else {
    Write-Host "Trae folder fully removed!"
}