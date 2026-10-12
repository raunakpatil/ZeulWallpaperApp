# Creates Zeul's release signing key (run once, by the app owner).
#
#   powershell -ExecutionPolicy Bypass -File android\scripts\make-release-key.ps1
#
# - You type the password here; it is never printed, never committed, never sent anywhere.
# - The keystore goes OUTSIDE the repo (default: %USERPROFILE%\zeul-keys\zeul-release.jks).
# - Writes android\keystore.properties (git-ignored) so `gradlew assembleRelease` can sign.
# - Prints the SHA-1 / SHA-256 fingerprints (needed for Google sign-in and app links).
#
# BACK UP the .jks file and the password (e.g. a password manager + a USB stick).
# If you lose them you can never publish an update that installs over this version.

$ErrorActionPreference = 'Stop'
$androidDir = Split-Path -Parent $PSScriptRoot
$propsFile = Join-Path $androidDir 'keystore.properties'
$keyDir = Join-Path $env:USERPROFILE 'zeul-keys'
$keystore = Join-Path $keyDir 'zeul-release.jks'
$alias = 'zeul'

$keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
if (-not $keytool) {
    $jdk = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -Filter 'jdk-*' -ErrorAction SilentlyContinue | Select-Object -Last 1
    if ($jdk) { $keytool = Join-Path $jdk.FullName 'bin\keytool.exe' }
}
if (-not $keytool -or -not (Test-Path $keytool)) { throw 'keytool not found. Install JDK 17+ or add it to PATH.' }

if (Test-Path $keystore) {
    Write-Host "A release key already exists at $keystore. Not overwriting it." -ForegroundColor Yellow
    exit 1
}
New-Item -ItemType Directory -Force $keyDir | Out-Null

function Read-Plain([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

$pw = Read-Plain 'Choose a release key password (min 8 characters)'
if ($pw.Length -lt 8) { throw 'Password too short.' }
if ($pw -ne (Read-Plain 'Type it again')) { throw 'Passwords do not match.' }

# Pass the password through an environment variable, not the command line.
$env:ZEUL_KS_PW = $pw
try {
    & $keytool -genkeypair -v -keystore $keystore -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 -validity 10000 `
        -dname 'CN=Zeul, O=Zeul, C=IN' -storepass:env ZEUL_KS_PW -keypass:env ZEUL_KS_PW | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed.' }

    $escapedPath = $keystore -replace '\\', '/'
    @(
        '# Release signing (git-ignored). Created by scripts/make-release-key.ps1.'
        "storeFile=$escapedPath"
        "storePassword=$pw"
        "keyAlias=$alias"
        "keyPassword=$pw"
    ) | Set-Content -Encoding ascii $propsFile

    Write-Host ''
    Write-Host "Release key created: $keystore" -ForegroundColor Green
    Write-Host "Signing config written: $propsFile (git-ignored)" -ForegroundColor Green
    Write-Host ''
    & $keytool -list -v -keystore $keystore -alias $alias -storepass:env ZEUL_KS_PW | Select-String 'SHA1:|SHA256:'
    Write-Host ''
    Write-Host 'Now BACK UP the .jks file and the password somewhere safe.' -ForegroundColor Yellow
} finally {
    Remove-Item Env:\ZEUL_KS_PW -ErrorAction SilentlyContinue
}
