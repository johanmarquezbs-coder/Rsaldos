$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$base = "$env:LOCALAPPDATA\Android"
$sdk = "$base\Sdk"
$imagen = "system-images;android-34;google_apis;x86_64"
New-Item -ItemType Directory -Force $base, $sdk | Out-Null

if (-not (Test-Path "$base\jdk17")) {
    Write-Host "Descargando JDK 17..."
    Invoke-WebRequest https://aka.ms/download-jdk/microsoft-jdk-17-windows-x64.zip -OutFile "$base\jdk.zip"
    Expand-Archive "$base\jdk.zip" "$base\jdk-tmp" -Force
    Move-Item (Get-ChildItem "$base\jdk-tmp" | Select-Object -First 1).FullName "$base\jdk17"
    Remove-Item "$base\jdk.zip", "$base\jdk-tmp" -Recurse -Force
}
$env:JAVA_HOME = "$base\jdk17"
$env:ANDROID_HOME = $sdk

if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
    Write-Host "Descargando herramientas del SDK..."
    Invoke-WebRequest https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip -OutFile "$base\cmdline.zip"
    Expand-Archive "$base\cmdline.zip" "$base\cmdline-tmp" -Force
    Remove-Item "$base\cmdline.zip"
    New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
    Copy-Item "$base\cmdline-tmp\cmdline-tools" "$sdk\cmdline-tools\latest" -Recurse -Force
    Remove-Item "$base\cmdline-tmp" -Recurse -Force
}
$bin = "$sdk\cmdline-tools\latest\bin"
$emulador = "$sdk\emulator\emulator.exe"
$adb = "$sdk\platform-tools\adb.exe"

if (-not (Test-Path $emulador) -or -not (Test-Path "$sdk\system-images\android-34")) {
    Write-Host "Instalando emulador e imagen de Android 14..."
    ((1..30 | ForEach-Object { "y" }) -join "`n") | & "$bin\sdkmanager.bat" --sdk_root=$sdk --licenses | Out-Null
    & "$bin\sdkmanager.bat" --sdk_root=$sdk "platform-tools" "emulator" $imagen
}

if (-not (& $emulador -list-avds | Select-String "^RSaldos$")) {
    "no" | & "$bin\avdmanager.bat" create avd -n RSaldos -k $imagen -d pixel_6
}

if ($args -contains "-solo-instalar") { & $emulador -accel-check; exit }

Write-Host "Abriendo el emulador..."
Start-Process $emulador -ArgumentList "-avd RSaldos -no-snapshot-save -gpu swiftshader_indirect -no-boot-anim"
& $adb wait-for-device
while ((& $adb shell getprop sys.boot_completed 2>$null) -ne "1") { Start-Sleep 3 }

New-Item -ItemType Directory -Force dist | Out-Null
Invoke-WebRequest http://localhost:8095/rsaldos.apk -OutFile dist\rsaldos.apk
& $adb install -r dist\rsaldos.apk
& $adb shell monkey -p com.rsaldos.app -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "R-Saldos abierta en el emulador."
