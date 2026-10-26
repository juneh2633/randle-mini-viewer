$ErrorActionPreference = 'Stop'
$taskRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$viewerRoot = Join-Path $taskRoot 'extension'
$releaseRoot = Join-Path $taskRoot 'dist'
$manifest = Get-Content -LiteralPath (Join-Path $viewerRoot 'manifest.json') -Raw | ConvertFrom-Json
$releaseName = "randle-mini-viewer-$($manifest.version).zip"
$allowed = @('manifest.json', 'viewer.js', 'content.js', 'icon.png', 'README.md')
$actual = @(Get-ChildItem -LiteralPath $viewerRoot -Force | ForEach-Object { $_.Name })
if (Compare-Object ($allowed | Sort-Object) ($actual | Sort-Object)) {
    throw 'Unexpected files in extension; release packaging stopped.'
}
if ($manifest.permissions -or $manifest.host_permissions -or $manifest.background) {
    throw 'Unexpected permissions or background service; release packaging stopped.'
}
New-Item -ItemType Directory -Path $releaseRoot -Force | Out-Null
$releasePath = Join-Path $releaseRoot $releaseName
$releaseFiles = @($allowed | ForEach-Object { Join-Path $viewerRoot $_ })
Compress-Archive -LiteralPath $releaseFiles -DestinationPath $releasePath -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($releasePath)
try {
    $entries = @($archive.Entries | ForEach-Object { $_.FullName })
    if (Compare-Object ($allowed | Sort-Object) ($entries | Sort-Object)) {
        throw 'ZIP contents do not match the release allowlist.'
    }
} finally { $archive.Dispose() }
$hash = (Get-FileHash -LiteralPath $releasePath -Algorithm SHA256).Hash.ToLowerInvariant()
Set-Content -LiteralPath (Join-Path $releaseRoot "$releaseName.sha256") -Value "$hash  $releaseName" -Encoding ascii
Write-Output "Ready: $releasePath"
Write-Output "SHA256: $hash"
