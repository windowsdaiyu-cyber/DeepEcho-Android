param(
    [string]$Root = "",
    [string]$Manifest = ""
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Root)) {
    $Root = Split-Path -Parent $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($Manifest)) {
    $Manifest = Join-Path $Root "PROTECTED-SOURCE-SHA256-1.9.0.txt"
}

$Root = (Resolve-Path -LiteralPath $Root).Path
if (-not (Test-Path -LiteralPath $Manifest)) {
    Write-Host "[ERROR] Protected-source hash manifest missing: $Manifest" -ForegroundColor Red
    exit 1
}

$failed = $false
Get-Content -LiteralPath $Manifest | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) { return }

    $parts = $line -split '\s{2,}', 2
    if ($parts.Count -ne 2) {
        Write-Host "[ERROR] Bad hash-manifest line: $line" -ForegroundColor Red
        $failed = $true
        return
    }

    $expected = $parts[0].Trim().ToLowerInvariant()
    $rel = $parts[1].Trim().Replace('/', '\')
    $path = Join-Path $Root $rel

    if (-not (Test-Path -LiteralPath $path)) {
        Write-Host "[ERROR] Protected file missing: $rel" -ForegroundColor Red
        $failed = $true
        return
    }

    $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        Write-Host "[ERROR] Protected file changed: $rel" -ForegroundColor Red
        Write-Host "Expected: $expected"
        Write-Host "Actual  : $actual"
        $failed = $true
    }
}

if ($failed) { exit 1 }
Write-Host "[OK] Protected app/source hashes match tested v1.9.0." -ForegroundColor Green
exit 0
