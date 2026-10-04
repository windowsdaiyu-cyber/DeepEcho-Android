param(
    [string]$Repo = "windowsdaiyu-cyber/DeepEcho-Android",
    [string]$Tag = "v1.9.0",
    [string]$Version = "1.9.0",
    [int]$TimeoutMinutes = 20
)

$ErrorActionPreference = 'SilentlyContinue'
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$api = "https://api.github.com/repos/$Repo/releases/tags/$Tag"
$headers = @{
    'Accept' = 'application/vnd.github+json'
    'User-Agent' = 'DEEP-ECHO-Mobile-Release-Waiter'
}

Write-Host "GitHub Actions release ka wait ho raha hai..." -ForegroundColor Cyan

while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-RestMethod -Uri $api -Headers $headers -Method Get
        if ($r -and -not $r.draft -and $r.tag_name -eq $Tag) {
            $names = @($r.assets | ForEach-Object { $_.name })
            $apk = "DEEP-ECHO-Mobile-$Version.apk"
            $sha = "$apk.sha256"

            if ($names -contains $apk -and $names -contains $sha) {
                Write-Host "[OK] GitHub Release published with APK + SHA256." -ForegroundColor Green
                Write-Host "Release: $($r.html_url)"
                exit 0
            }
        }
    } catch {}

    Start-Sleep -Seconds 10
}

Write-Host "[ERROR] Release verification timed out." -ForegroundColor Red
Write-Host "GitHub Actions check karo:"
Write-Host "https://github.com/$Repo/actions"
exit 1
