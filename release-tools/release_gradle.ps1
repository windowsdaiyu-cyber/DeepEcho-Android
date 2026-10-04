param(
    [Parameter(Mandatory=$true)][string]$GradleBat,
    [Parameter(Mandatory=$true)][string]$LogPath
)

$ErrorActionPreference = 'Continue'
$args = @(
    '--build-cache',
    '--console=plain',
    ':app:testDebugUnitTest',
    ':app:assembleRelease',
    '--stacktrace'
)

New-Item -ItemType File -Force -Path $LogPath | Out-Null

& $GradleBat @args 2>&1 | Tee-Object -FilePath $LogPath
$rc = $LASTEXITCODE
if ($null -eq $rc) { $rc = 1 }
exit $rc
