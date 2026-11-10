param([switch]$SoloCompilar)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
Set-Location $PSScriptRoot

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
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

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

$paquetes = @("platform-tools", "emulator", "platforms;android-34", "build-tools;34.0.0", $imagen)
$faltan = @($paquetes | Where-Object { -not (Test-Path ("$sdk\" + ($_ -replace ';', '\'))) })
if ($faltan.Count -gt 0) {
    Write-Host "Instalando en el SDK: $($faltan -join ', ')"
    ((1..30 | ForEach-Object { "y" }) -join "`n") | & "$bin\sdkmanager.bat" --sdk_root=$sdk --licenses | Out-Null
    & "$bin\sdkmanager.bat" --sdk_root=$sdk @faltan
}

Write-Host "Compilando el APK..."
"sdk.dir=$($sdk -replace '\\', '\\')" | Set-Content -Encoding ASCII local.properties
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { throw "La compilacion fallo. Revisa el error de arriba." }
$apk = (Resolve-Path "app\build\outputs\apk\debug\app-debug.apk").Path
Write-Host "APK listo: $apk"
if ($SoloCompilar) { exit }

$ErrorActionPreference = "Continue"
if (-not (& $emulador -list-avds | Select-String "^RSaldos$")) {
    "no" | & "$bin\avdmanager.bat" create avd -n RSaldos -k $imagen -d pixel_6
}

$enLinea = & $adb devices | Select-String "^emulator-\d+\s+device$"
if (-not $enLinea) {
    Get-Process qemu-system*, emulator* -ErrorAction SilentlyContinue | Stop-Process -Force
    Remove-Item "$env:USERPROFILE\.android\avd\RSaldos.avd\*.lock" -Recurse -Force -ErrorAction SilentlyContinue
    & $adb kill-server | Out-Null
    Write-Host "Abriendo el emulador..."
    $proceso = Start-Process $emulador -ArgumentList "-avd RSaldos -no-snapshot-load -gpu swiftshader_indirect -no-boot-anim" -PassThru
    $limite = (Get-Date).AddMinutes(6)
    while ((& $adb shell getprop sys.boot_completed 2>$null) -ne "1") {
        if ($proceso.HasExited) { throw "El emulador se cerro. Ejecuta: & `"$emulador`" -avd RSaldos -verbose" }
        if ((Get-Date) -gt $limite) { throw "Android no termino de arrancar en 6 minutos." }
        Start-Sleep 3
    }
}

& $adb install -r $apk
& $adb shell monkey -p com.rsaldos.app -c android.intent.category.LAUNCHER 1 | Out-Null
Write-Host "R-Saldos abierta en el emulador."
