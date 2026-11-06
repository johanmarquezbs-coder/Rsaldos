#!/bin/sh
DISPOSITIVO=emulador:5555

echo "Esperando el emulador..."
until [ "$(adb -s $DISPOSITIVO shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  adb connect $DISPOSITIVO > /dev/null 2>&1
  sleep 5
done

adb -s $DISPOSITIVO install -r /out/rsaldos-debug.apk
adb -s $DISPOSITIVO shell monkey -p com.rsaldos.app -c android.intent.category.LAUNCHER 1 > /dev/null
echo "R-Saldos instalada. Ábrela en http://localhost:6080"
