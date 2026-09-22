$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    & .\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease bundleRelease
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed" }

    [xml[]]$results = Get-ChildItem app\build\test-results\testDebugUnitTest -Filter 'TEST-*.xml' |
        ForEach-Object { [xml](Get-Content -LiteralPath $_.FullName) }
    $tests = ($results.testsuite | Measure-Object -Property tests -Sum).Sum
    $failures = ($results.testsuite | Measure-Object -Property failures -Sum).Sum
    $errors = ($results.testsuite | Measure-Object -Property errors -Sum).Sum
    if ($failures -ne 0 -or $errors -ne 0) { throw "Unit tests failed" }

    [xml]$lint = Get-Content -LiteralPath app\build\reports\lint-results-debug.xml
    $lintErrors = @($lint.issues.issue | Where-Object severity -eq 'Error').Count
    if ($lintErrors -ne 0) { throw "Android Lint reported $lintErrors errors" }

    Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
    "Verified: $tests unit tests, 0 failures, 0 lint errors"
} finally {
    Pop-Location
}
