param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [switch]$SkipResourceCopy
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$sourcePath = [System.IO.Path]::GetFullPath($Source)

if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
    throw "DLL not found: $sourcePath"
}

if ([System.IO.Path]::GetFileName($sourcePath).ToLowerInvariant() -ne 'openyap_native.dll') {
    throw "Expected a file named openyap_native.dll, got: $sourcePath"
}

$prebuiltDir = Join-Path $PSScriptRoot 'prebuilt\windows-x64'
$prebuiltPath = Join-Path $prebuiltDir 'openyap_native.dll'

New-Item -ItemType Directory -Force -Path $prebuiltDir | Out-Null
Copy-Item -LiteralPath $sourcePath -Destination $prebuiltPath -Force

$copied = @($prebuiltPath)

if (-not $SkipResourceCopy) {
    $resourceDir = Join-Path $repoRoot 'composeApp\resources\windows-x64'
    $resourcePath = Join-Path $resourceDir 'openyap_native.dll'
    New-Item -ItemType Directory -Force -Path $resourceDir | Out-Null
    Copy-Item -LiteralPath $sourcePath -Destination $resourcePath -Force
    $copied += $resourcePath
}

Write-Host 'Staged native DLL:'
foreach ($path in $copied) {
    Write-Host " - $path"
}

Write-Host ''
Write-Host 'You can now run:'
Write-Host ' - .\gradlew :composeApp:run'
