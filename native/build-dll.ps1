<#
.SYNOPSIS
    Builds openyap_native.dll for x64 and stages it into prebuilt/windows-x64/.

.DESCRIPTION
    Locates the latest Visual Studio installation that has the C++ toolchain,
    bootstraps the x64 MSVC environment via vcvarsall.bat, then runs:

        cmake --preset x64-release
        cmake --build --preset x64-release -j <cpu-count>
        cmake --install out/build/x64-release   (stages to prebuilt/windows-x64/)

    Finally calls stage-dll.ps1 to also copy the DLL into
    composeApp/resources/windows-x64/ so a Gradle run picks it up immediately.

.PARAMETER Config
    Build configuration. "Release" (default) or "Debug".

.PARAMETER SkipStage
    Skip the final stage-dll.ps1 call (useful if you only want the raw DLL).

.PARAMETER Jobs
    Number of parallel jobs passed to cmake --build. Defaults to the logical
    processor count.

.EXAMPLE
    # Standard release build + stage:
    .\build-dll.ps1

.EXAMPLE
    # Debug build without staging:
    .\build-dll.ps1 -Config Debug -SkipStage

.NOTES
    Must be run from a normal PowerShell prompt — the script sets up the MSVC
    x64 environment internally via vcvarsall.bat, so you do NOT need to open
    an "x64 Native Tools Command Prompt" first.
#>

param(
    [ValidateSet('Release', 'Debug')]
    [string]$Config = 'Release',

    [switch]$SkipStage,

    [int]$Jobs = [Environment]::ProcessorCount
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# ── Paths ─────────────────────────────────────────────────────────────────────

$scriptDir = $PSScriptRoot                                   # native/
$repoRoot  = Split-Path -Parent $scriptDir                   # repo root
$preset    = if ($Config -eq 'Release') { 'x64-release' } else { 'x64-debug' }
$buildDir  = Join-Path $scriptDir "out\build\$preset"
$dllName   = 'openyap_native.dll'

# ── Locate Visual Studio via vswhere ──────────────────────────────────────────

$vswhere = Join-Path ${env:ProgramFiles(x86)} `
    'Microsoft Visual Studio\Installer\vswhere.exe'

if (-not (Test-Path $vswhere)) {
    throw @"
vswhere.exe not found at:
  $vswhere

Install Visual Studio 2019 or later with the 'Desktop development with C++'
workload, then re-run this script.
"@
}

Write-Host 'Locating Visual Studio C++ toolchain...' -ForegroundColor Cyan

$vsPath = & $vswhere `
    -latest `
    -products '*' `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    -property installationPath

if (-not $vsPath) {
    throw @"
No Visual Studio installation with the C++ workload was found.

Open the Visual Studio Installer and add the
'Desktop development with C++' workload, then re-run this script.
"@
}

$vcvarsall = Join-Path $vsPath 'VC\Auxiliary\Build\vcvarsall.bat'

if (-not (Test-Path $vcvarsall)) {
    throw "vcvarsall.bat not found in: $vsPath"
}

Write-Host "Found VS at: $vsPath" -ForegroundColor Green

# ── Build the cmake command sequence ──────────────────────────────────────────
# Everything runs in a single cmd.exe invocation so that the environment set
# by vcvarsall.bat (LIB, INCLUDE, PATH, etc.) is inherited by cmake.

$cmakeConfigure = "cmake --preset `"$preset`" `"$scriptDir`""
$cmakeBuild     = "cmake --build --preset `"$preset`" -j $Jobs"
$cmakeInstall   = "cmake --install `"$buildDir`""

$batchLines = @(
    "@echo off",
    "call `"$vcvarsall`" x64",
    "if errorlevel 1 exit /b 1",
    "",
    "echo.",
    "echo [build-dll] Configuring ($preset)...",
    $cmakeConfigure,
    "if errorlevel 1 exit /b 1",
    "",
    "echo.",
    "echo [build-dll] Building ($Config, $Jobs jobs)...",
    $cmakeBuild,
    "if errorlevel 1 exit /b 1",
    "",
    "echo.",
    "echo [build-dll] Installing / staging to prebuilt/...",
    $cmakeInstall,
    "if errorlevel 1 exit /b 1"
)

$tempBat = [System.IO.Path]::GetTempFileName() + '.bat'
$batchLines | Set-Content -Path $tempBat -Encoding ASCII

# ── Execute ───────────────────────────────────────────────────────────────────

Write-Host ''
Write-Host "=== openyap_native build ($Config / x64) ===" -ForegroundColor Cyan
Write-Host ''

try {
    $proc = Start-Process `
        -FilePath 'cmd.exe' `
        -ArgumentList "/c `"$tempBat`"" `
        -NoNewWindow `
        -PassThru `
        -Wait

    if ($proc.ExitCode -ne 0) {
        throw "cmake build failed (exit code $($proc.ExitCode))."
    }
}
finally {
    Remove-Item -LiteralPath $tempBat -ErrorAction SilentlyContinue
}

# ── Stage to composeApp/resources as well ─────────────────────────────────────

if (-not $SkipStage) {
    $prebuiltDll = Join-Path $scriptDir "prebuilt\windows-x64\$dllName"

    if (-not (Test-Path $prebuiltDll)) {
        throw "cmake --install succeeded but $dllName was not found at: $prebuiltDll"
    }

    $stageDll = Join-Path $scriptDir 'stage-dll.ps1'
    Write-Host ''
    Write-Host '[build-dll] Staging DLL to composeApp resources...' -ForegroundColor Cyan
    & $stageDll -Source $prebuiltDll
}

Write-Host ''
Write-Host '=== Build complete ===' -ForegroundColor Green
Write-Host ''
Write-Host 'The freshly built DLL is in:'
Write-Host "  $scriptDir\prebuilt\windows-x64\$dllName"
if (-not $SkipStage) {
    Write-Host "  $repoRoot\composeApp\resources\windows-x64\$dllName"
}
Write-Host ''
Write-Host 'You can now run:'
Write-Host '  .\gradlew :composeApp:run'
