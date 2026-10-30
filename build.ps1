# XDL build script (no Android Studio needed)
# usage:
#   .\build.ps1                  -> assembleDebug
#   .\build.ps1 assembleRelease  -> builds and archives to dist\XDL-<version>.apk
$ErrorActionPreference = "Stop"

$env:JAVA_HOME        = "D:\scrapkit\xtools\zulu17.54.21-ca-jdk17.0.13-win_x64"
$env:ANDROID_HOME     = "C:\Users\ASUS\AppData\Local\Android\Sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH             = "$env:JAVA_HOME\bin;$env:PATH"

Set-Location $PSScriptRoot

$task = "assembleDebug"
if ($args.Count -gt 0) { $task = $args[0] }
& "D:\scrapkit\xtools\gradle-8.13\bin\gradle.bat" $task --console=plain

if ($LASTEXITCODE -ne 0) {
    Write-Host "BUILD FAILED exit=$LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkRoot = Join-Path $PSScriptRoot "app\build\outputs\apk"
Get-ChildItem $apkRoot -Recurse -Filter *.apk | ForEach-Object {
    "APK -> {0}  ({1:N1} MB)" -f $_.FullName, ($_.Length / 1MB)
}

$relApk = Join-Path $apkRoot "release\app-release.apk"
if (Test-Path $relApk) {
    $m = Select-String -Path (Join-Path $PSScriptRoot "app\build.gradle.kts") -Pattern 'versionName\s*=\s*"([^"]+)"'
    $ver = "unknown"
    if ($m) { $ver = $m.Matches[0].Groups[1].Value }
    $dist = Join-Path $PSScriptRoot "dist"
    if (-not (Test-Path $dist)) { New-Item -ItemType Directory -Path $dist | Out-Null }
    $dest = Join-Path $dist ("XDL-" + $ver + ".apk")
    Copy-Item $relApk $dest -Force
    Write-Host "ARCHIVED -> $dest" -ForegroundColor Green
}
