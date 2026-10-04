param(
    [string]$Source = "",
    [string]$Destination = ""
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Source)) {
    $Source = Split-Path -Parent $PSScriptRoot
}
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $env:USERPROFILE "DeepEcho-Android-Local\release-work-1.9.0\repo"
}

$Source = (Resolve-Path -LiteralPath $Source).Path
$Destination = (Resolve-Path -LiteralPath $Destination).Path

if (-not (Test-Path -LiteralPath (Join-Path $Destination '.git'))) {
    throw "Destination is not a Git clone: $Destination"
}

# Preserve only clone Git metadata.
Get-ChildItem -LiteralPath $Destination -Force |
    Where-Object { $_.Name -ne '.git' } |
    Remove-Item -Recurse -Force

$excludedNames = @(
    '.git', '.gradle', '.idea', 'build',
    'deepecho.keystore', 'local.properties',
    'deepecho-build.log', 'release-build.log',
    '_deepecho_gradle.bat'
)

Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
    if ($excludedNames -contains $_.Name) { return }

    if ($_.Name -eq 'app') {
        $target = Join-Path $Destination 'app'
        Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force
        $appBuild = Join-Path $target 'build'
        if (Test-Path $appBuild) { Remove-Item $appBuild -Recurse -Force }
        return
    }

    $target = Join-Path $Destination $_.Name
    if ($_.PSIsContainer) {
        Copy-Item -LiteralPath $_.FullName -Destination $target -Recurse -Force
    } else {
        Copy-Item -LiteralPath $_.FullName -Destination $target -Force
    }
}

@(
    (Join-Path $Destination '.gradle'),
    (Join-Path $Destination '.idea'),
    (Join-Path $Destination 'build'),
    (Join-Path $Destination 'app\build'),
    (Join-Path $Destination 'deepecho.keystore'),
    (Join-Path $Destination 'local.properties'),
    (Join-Path $Destination 'deepecho-build.log'),
    (Join-Path $Destination 'release-build.log'),
    (Join-Path $Destination '_deepecho_gradle.bat')
) | ForEach-Object {
    if (Test-Path $_) { Remove-Item $_ -Recurse -Force }
}

Write-Host "[OK] Exact Android release source synced into safe clone." -ForegroundColor Green
exit 0
