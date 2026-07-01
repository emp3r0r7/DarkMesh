param(
    [string]$Unishox2Dir = "third_party/Unishox2",
    [string]$OutDir = "app/src/main/jniLibs",
    [string]$SdkDir = "",
    [string]$NdkVersion = "",
    [int]$Api = 24
)

$ErrorActionPreference = "Stop"

function Resolve-SdkDir {
    param([string]$ExplicitSdkDir)

    if ($ExplicitSdkDir) {
        return (Resolve-Path -LiteralPath $ExplicitSdkDir).Path
    }

    if ($env:ANDROID_HOME) {
        return $env:ANDROID_HOME
    }

    if ($env:ANDROID_SDK_ROOT) {
        return $env:ANDROID_SDK_ROOT
    }

    $localProperties = Join-Path $PSScriptRoot "local.properties"
    if (Test-Path -LiteralPath $localProperties) {
        $sdkLine = Get-Content -LiteralPath $localProperties |
            Where-Object { $_ -match "^sdk\.dir=" } |
            Select-Object -First 1
        if ($sdkLine) {
            return ($sdkLine -replace "^sdk\.dir=", "") -replace "\\:", ":"
        }
    }

    throw "Android SDK not found. Pass -SdkDir or set ANDROID_HOME."
}

function Resolve-NdkDir {
    param(
        [string]$ResolvedSdkDir,
        [string]$ExplicitNdkVersion
    )

    $ndkRoot = Join-Path $ResolvedSdkDir "ndk"
    if ($ExplicitNdkVersion) {
        $dir = Join-Path $ndkRoot $ExplicitNdkVersion
        if (Test-Path -LiteralPath $dir) {
            return (Resolve-Path -LiteralPath $dir).Path
        }
        throw "NDK version not found: $ExplicitNdkVersion"
    }

    $latest = Get-ChildItem -LiteralPath $ndkRoot -Directory |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if (!$latest) {
        throw "No NDK found under $ndkRoot"
    }

    return $latest.FullName
}

$repoRoot = $PSScriptRoot
$sourceDir = Resolve-Path -LiteralPath (Join-Path $repoRoot $Unishox2Dir)
$sourceFile = Join-Path $sourceDir "unishox2.c"

if (!(Test-Path -LiteralPath $sourceFile)) {
    throw "Missing $sourceFile. Clone Unishox2 into $Unishox2Dir first."
}

$resolvedSdkDir = Resolve-SdkDir $SdkDir
$resolvedNdkDir = Resolve-NdkDir $resolvedSdkDir $NdkVersion
$toolchainBin = Join-Path $resolvedNdkDir "toolchains\llvm\prebuilt\windows-x86_64\bin"
$outRoot = Join-Path $repoRoot $OutDir

$targets = @(
    @{ Abi = "arm64-v8a"; Triple = "aarch64-linux-android" },
    @{ Abi = "armeabi-v7a"; Triple = "armv7a-linux-androideabi" },
    @{ Abi = "x86"; Triple = "i686-linux-android" },
    @{ Abi = "x86_64"; Triple = "x86_64-linux-android" }
)

foreach ($target in $targets) {
    $clang = Join-Path $toolchainBin "$($target.Triple)$Api-clang.cmd"
    if (!(Test-Path -LiteralPath $clang)) {
        throw "Missing compiler: $clang"
    }

    $abiOut = Join-Path $outRoot $target.Abi
    [System.IO.Directory]::CreateDirectory($abiOut) | Out-Null

    $output = Join-Path $abiOut "libunishox2.so"
    & $clang `
        -shared `
        -fPIC `
        -O2 `
        -std=c99 `
        -DUNISHOX_API_WITH_OUTPUT_LEN=1 `
        "-Wl,-z,max-page-size=16384" `
        "-Wl,-soname,libunishox2.so" `
        -Wall `
        -Wextra `
        -o $output `
        $sourceFile

    if ($LASTEXITCODE -ne 0) {
        throw "Build failed for $($target.Abi)"
    }

    Write-Host "Built $output"
}
