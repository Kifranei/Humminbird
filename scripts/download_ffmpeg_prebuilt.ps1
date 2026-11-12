$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$Tag = "1.1.7"
$ArchiveUrl = "https://codeload.github.com/Kifranei/Halcyon/zip/refs/tags/$Tag"
$ExpectedSha256 = "4D3491D429677C22DB9782AEC1C61BB82E7F2C68A99B4C14D956A3D058004A4C"
$TempRoot = Join-Path $RepoRoot ".tmp\ffmpeg-prebuilt"
$ArchivePath = Join-Path $TempRoot "Halcyon-$Tag.zip"
$ExtractRoot = Join-Path $TempRoot "extract"
$TargetRoot = Join-Path $RepoRoot "ffmpeg-decoder\src\main\jni\ffmpeg"

Write-Host "Downloading FFmpeg prebuilt inputs from tag $Tag..."
New-Item -ItemType Directory -Force -Path $TempRoot | Out-Null
if (Test-Path $ArchivePath) {
    Remove-Item -LiteralPath $ArchivePath -Force
}
curl.exe -L $ArchiveUrl -o $ArchivePath
if ($LASTEXITCODE -ne 0) {
    throw "Failed to download $ArchiveUrl"
}

$actualSha256 = (Get-FileHash $ArchivePath -Algorithm SHA256).Hash.ToUpperInvariant()
if ($actualSha256 -ne $ExpectedSha256) {
    throw "SHA256 mismatch for $ArchivePath. Expected $ExpectedSha256 but got $actualSha256"
}

if (Test-Path $ExtractRoot) {
    Remove-Item -LiteralPath $ExtractRoot -Recurse -Force
}
Expand-Archive -Path $ArchivePath -DestinationPath $ExtractRoot -Force

$SnapshotRoot = $null
$SnapshotRootCandidates = @(
    (Join-Path $ExtractRoot "Halcyon-$Tag\ffmpeg-decoder\src\main\jni\ffmpeg"),
    (Join-Path $ExtractRoot "Ella-$Tag\ffmpeg-decoder\src\main\jni\ffmpeg")
)
foreach ($Candidate in $SnapshotRootCandidates) {
    if (Test-Path $Candidate) {
        $SnapshotRoot = $Candidate
        break
    }
}
if ($null -eq $SnapshotRoot) {
    throw "Downloaded archive does not contain a Halcyon/Ella ffmpeg snapshot directory."
}
$IncludeSource = Join-Path $SnapshotRoot "include"
$StaticLibSource = Join-Path $SnapshotRoot "android-libs"

if (-not (Test-Path $IncludeSource) -or -not (Test-Path $StaticLibSource)) {
    throw "Downloaded archive does not contain ffmpeg prebuilt include/android-libs directories."
}

if (Test-Path $TargetRoot) {
    Remove-Item -LiteralPath $TargetRoot -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $TargetRoot | Out-Null
Copy-Item -LiteralPath $IncludeSource -Destination (Join-Path $TargetRoot "include") -Recurse -Force
Copy-Item -LiteralPath $StaticLibSource -Destination (Join-Path $TargetRoot "android-libs") -Recurse -Force

Write-Host "Restored FFmpeg prebuilt inputs to $TargetRoot"
Write-Host "These files stay ignored by Git. Normal assembleDebug still uses the packaged libffmpegJNI.so."
