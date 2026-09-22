$ErrorActionPreference = 'Stop'

$required = @(
    'WRIST_RELAY_KEYSTORE',
    'WRIST_RELAY_STORE_PASSWORD',
    'WRIST_RELAY_KEY_ALIAS',
    'WRIST_RELAY_KEY_PASSWORD'
)
$missing = $required | Where-Object { [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) }
if ($missing.Count -gt 0) {
    throw "Missing release signing variables: $($missing -join ', ')"
}

$projectRoot = (Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$keystore = (Resolve-Path -LiteralPath $env:WRIST_RELAY_KEYSTORE).Path
if ($keystore.StartsWith($projectRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "The release keystore must be stored outside the repository"
}

Push-Location $projectRoot
try {
    & .\gradlew.bat clean testDebugUnitTest lintDebug assembleRelease bundleRelease
    if ($LASTEXITCODE -ne 0) { throw "Signed release build failed" }

    $apk = (Resolve-Path app\build\outputs\apk\release\app-release.apk).Path
    $aab = (Resolve-Path app\build\outputs\bundle\release\app-release.aab).Path
    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    $apksigner = Get-ChildItem -LiteralPath "$sdkRoot\build-tools" -Filter apksigner.bat -Recurse |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $apksigner) { throw "apksigner.bat was not found" }

    & $apksigner.FullName verify --verbose --print-certs $apk
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed" }
    & jarsigner -verify -strict -certs $aab
    if ($LASTEXITCODE -ne 0) { throw "AAB signature verification failed" }

    Get-FileHash $apk, $aab -Algorithm SHA256
} finally {
    Pop-Location
}
