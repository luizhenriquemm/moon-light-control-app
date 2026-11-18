# Creates the release *upload key* for Google Play and the properties file Gradle reads.
# Run it ONCE, in your own PowerShell window:
#
#     powershell -ExecutionPolicy Bypass -File tools\create-upload-key.ps1
#
# Files go to %USERPROFILE%\.android-signing\ (outside the repository, never commit them):
#   moonlightcontrol-upload.jks        the key (PKCS12)
#   moonlightcontrol.properties        path + password, read by androidApp/build.gradle.kts
# BACK UP BOTH FILES (and the password) somewhere safe. With Play App Signing a lost upload key can
# be reset through Google support, but it takes days; without a backup, sideloaded installs can only
# be updated by uninstalling first.

$ErrorActionPreference = 'Stop'

$dir = Join-Path $env:USERPROFILE '.android-signing'
$keystore = Join-Path $dir 'moonlightcontrol-upload.jks'
$properties = Join-Path $dir 'moonlightcontrol.properties'
$keytool = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe'

if (Test-Path $keystore) {
    Write-Host "The key already exists: $keystore (nothing changed)." -ForegroundColor Yellow
    exit 1
}
if (-not (Test-Path $keytool)) {
    Write-Host "keytool not found at $keytool (is Android Studio installed?)" -ForegroundColor Red
    exit 1
}

function Read-Secret([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

Write-Host 'Choose a password for the signing key (at least 8 characters).' -ForegroundColor Cyan
Write-Host 'Escolha uma senha para a chave de assinatura (pelo menos 8 caracteres).' -ForegroundColor Cyan
$password = Read-Secret 'Password / Senha'
$again = Read-Secret 'Repeat / Repita'
if ($password -ne $again) { Write-Host 'The passwords differ / As senhas são diferentes.' -ForegroundColor Red; exit 1 }
if ($password.Length -lt 8) { Write-Host 'Too short / Muito curta.' -ForegroundColor Red; exit 1 }
if ($password -match '[\\\r\n]') { Write-Host 'Do not use \ or line breaks / Não use \ nem quebras de linha.' -ForegroundColor Red; exit 1 }

New-Item -ItemType Directory -Force $dir | Out-Null

# The password goes to keytool through an environment variable, not the command line.
$env:MLC_SIGNING_PASSWORD = $password
try {
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias upload `
        -keyalg RSA -keysize 4096 -validity 10000 `
        -dname 'CN=Moon Light Control, O=Moon Audio, C=BR' `
        -storepass:env MLC_SIGNING_PASSWORD -keypass:env MLC_SIGNING_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw "keytool failed ($LASTEXITCODE)" }
} finally {
    Remove-Item Env:MLC_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}

$storePath = $keystore -replace '\\', '/'
@(
    '# Release signing for Moon Light Control. Keep private; back up together with the .jks file.'
    "storeFile=$storePath"
    "storePassword=$password"
    'keyAlias=upload'
    "keyPassword=$password"
) | Set-Content -Path $properties -Encoding ASCII

Write-Host ''
Write-Host "Created / Criado:" -ForegroundColor Green
Write-Host "  $keystore"
Write-Host "  $properties"
Write-Host 'Back up both files and the password! / Faça backup dos dois arquivos e da senha!' -ForegroundColor Yellow
