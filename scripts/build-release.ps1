<#
Prompts for the release keystore path, alias, and both passwords (masked), sets them as
process-scoped env vars for this script only, runs the signed release build, then clears them.
Nothing is written to disk, persisted env vars, or shell history.
#>

$defaultKeystore = $env:VERITYSEE_KEYSTORE
$keystorePath = Read-Host "Keystore path$(if ($defaultKeystore) { " [$defaultKeystore]" })"
if ([string]::IsNullOrWhiteSpace($keystorePath)) { $keystorePath = $defaultKeystore }

$defaultAlias = $env:VERITYSEE_KEY_ALIAS
$alias = Read-Host "Key alias$(if ($defaultAlias) { " [$defaultAlias]" })"
if ([string]::IsNullOrWhiteSpace($alias)) { $alias = $defaultAlias }

$storePassSecure = Read-Host "Keystore password" -AsSecureString
$keyPassSecure = Read-Host "Key password" -AsSecureString

function ConvertFrom-SecureStringPlain($secure) {
    $ptr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

$env:VERITYSEE_KEYSTORE = $keystorePath
$env:VERITYSEE_KEY_ALIAS = $alias
$env:VERITYSEE_KEYSTORE_PASSWORD = ConvertFrom-SecureStringPlain $storePassSecure
$env:VERITYSEE_KEY_PASSWORD = ConvertFrom-SecureStringPlain $keyPassSecure

try {
    Push-Location (Join-Path $PSScriptRoot "..")
    & .\gradlew.bat :app:assembleRelease --no-daemon
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
    # Scrub the secrets from this process's environment once the build is done.
    Remove-Item Env:VERITYSEE_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:VERITYSEE_KEY_PASSWORD -ErrorAction SilentlyContinue
}

if ($exitCode -eq 0) {
    Write-Host "`nBuild succeeded: app\build\outputs\apk\release\app-release.apk" -ForegroundColor Green
} else {
    Write-Host "`nBuild failed (exit code $exitCode)." -ForegroundColor Red
}
exit $exitCode
