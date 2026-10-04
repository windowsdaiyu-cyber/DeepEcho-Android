param(
    [Parameter(Mandatory=$true)][string]$GradleBat,
    [Parameter(Mandatory=$true)][string]$LogPath,
    [switch]$Offline
)

$ErrorActionPreference = 'Continue'
$gradleArgs = @()
if ($Offline) { $gradleArgs += '--offline' }
$gradleArgs += @('--build-cache','--parallel','--console=plain',':app:assembleDebug','--stacktrace')

# Ensure the log exists even if Gradle fails immediately.
New-Item -ItemType File -Force -Path $LogPath | Out-Null

& $GradleBat @gradleArgs 2>&1 | Tee-Object -FilePath $LogPath
$rc = $LASTEXITCODE
if ($null -eq $rc) { $rc = 1 }
exit $rc
