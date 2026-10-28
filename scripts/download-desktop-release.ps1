# PulseWave Automatic Release Downloader
# Automatically downloads the latest PulseWave APK release from GitHub to the Desktop

[CmdletBinding()]
param (
    [string]$Repo = "BSFrameworks5253/Plusewave",
    [string]$TargetDir = ""
)

$ErrorActionPreference = "Stop"

# Auto-detect Desktop directory (handling OneDrive or standard Desktop)
if ([string]::IsNullOrWhiteSpace($TargetDir)) {
    $desktop = [Environment]::GetFolderPath("Desktop")
    if (-not (Test-Path $desktop)) {
        $desktop = "$env:USERPROFILE\Desktop"
    }
    $TargetDir = $desktop
}

$buildsDir = Join-Path $TargetDir "PulseWave_Builds"
if (-not (Test-Path $buildsDir)) {
    New-Item -ItemType Directory -Path $buildsDir -Force | Out-Null
}

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  PulseWave Release Downloader for Desktop" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "Checking latest release from $Repo..." -ForegroundColor Yellow

try {
    $releaseUrl = "https://api.github.com/repos/$Repo/releases/latest"
    $headers = @{ "User-Agent" = "PulseWave-Desktop-Downloader" }
    
    $release = Invoke-RestMethod -Uri $releaseUrl -Headers $headers -Method Get
} catch {
    Write-Warning "Could not fetch /releases/latest, falling back to all releases query..."
    $releasesUrl = "https://api.github.com/repos/$Repo/releases?per_page=1"
    $releases = Invoke-RestMethod -Uri $releasesUrl -Headers $headers -Method Get
    $release = $releases[0]
}

if (-not $release) {
    Write-Error "No releases found for repository $Repo"
    exit 1
}

$tagName = $release.tag_name
$releaseName = $release.name
Write-Host "Found Release: $releaseName ($tagName)" -ForegroundColor Green

# Find the APK asset
$apkAsset = $release.assets | Where-Object { $_.name -like "*.apk" } | Select-Object -First 1

if (-not $apkAsset) {
    Write-Error "No APK asset found in release $tagName"
    exit 1
}

$apkName = $apkAsset.name
$apkSizeMB = [math]::Round($apkAsset.size / 1MB, 2)
$downloadUrl = $apkAsset.browser_download_url

$destBuildFile = Join-Path $buildsDir $apkName
$desktopLatestFile = Join-Path $TargetDir "PulseWave-Latest.apk"

Write-Host "File Name: $apkName" -ForegroundColor Cyan
Write-Host "File Size: $apkSizeMB MB" -ForegroundColor Cyan
Write-Host "Saving to:" -ForegroundColor Yellow
Write-Host "  1) $destBuildFile" -ForegroundColor Gray
Write-Host "  2) $desktopLatestFile" -ForegroundColor Gray

# Check if already downloaded
$alreadyDownloaded = $false
if (Test-Path $destBuildFile) {
    $existingSize = (Get-Item $destBuildFile).Length
    if ($existingSize -eq $apkAsset.size) {
        $alreadyDownloaded = $true
        Write-Host "APK is already up-to-date in PulseWave_Builds folder." -ForegroundColor Green
    }
}

if (-not $alreadyDownloaded) {
    Write-Host "Downloading APK directly from GitHub..." -ForegroundColor Yellow
    # Download using curl.exe or BitsTransfer for maximum reliability
    & curl.exe -L -o "$destBuildFile" "$downloadUrl" --progress-bar
    Write-Host "Download complete!" -ForegroundColor Green
}

# Copy to Desktop root as PulseWave-Latest.apk for quick access
Copy-Item -Path $destBuildFile -Destination $desktopLatestFile -Force
Write-Host "Desktop shortcut/file updated: $desktopLatestFile" -ForegroundColor Green

# Also download changelog if available
$changelogAsset = $release.assets | Where-Object { $_.name -like "*changelog*.json" } | Select-Object -First 1
if ($changelogAsset) {
    $changelogDest = Join-Path $buildsDir "$($tagName)_changelog.json"
    & curl.exe -sL -o "$changelogDest" "$($changelogAsset.browser_download_url)"
}

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "SUCCESS: APK is saved on your Desktop!" -ForegroundColor Green
Write-Host "Path: $destBuildFile" -ForegroundColor White
Write-Host "Latest: $desktopLatestFile" -ForegroundColor White
Write-Host "=================================================" -ForegroundColor Cyan
