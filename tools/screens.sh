#!/usr/bin/env bash
# Скриншоты экранов на эмуляторе для проверки интерфейса до релиза (отладочная сборка с демо-данными).
# Снимки — в screens/*.png; workflow выкладывает их в пререлиз «screens».
set -u
APK=app/build/outputs/apk/debug/app-debug.apk
PKG=ru.kurspocheku
ACT=app.dirhamrub.MainActivity  # класс — в пакете кода, не в идентификаторе приложения
OUT=screens
mkdir -p "$OUT"

adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.CAMERA
# Эмулятор в CI медленный: системные окна «… isn't responding» закрывали снимки — прячем их
# и даём системе успокоиться после загрузки.
adb shell settings put global hide_error_dialogs 1
sleep 30

# shot <имя> [extra...]: свежий запуск с демо-данными и снимок экрана.
shot() {
  local name=$1
  shift
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/$ACT" --ez demo true "$@" > /dev/null
  sleep "${WAIT:-7}"
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
  sleep 1
  adb exec-out screencap -p > "$OUT/$name.png"
  echo "снимок: $name"
}

adb shell cmd uimode night yes
adb shell settings put system font_scale 1.0
WAIT=15 shot 01-main
shot 02-cheat --es screen cheat
shot 03-currencies --es screen currencies
WAIT=12 shot 04-editor --es screen editor
WAIT=10 shot 05-camera --es screen camera

adb shell cmd uimode night no
shot 06-main-light
WAIT=12 shot 07-editor-light --es screen editor

adb shell cmd uimode night yes
adb shell settings put system font_scale 1.3
shot 08-main-font130
adb shell settings put system font_scale 1.0

# Короткий телефон (≈ 730 dp по высоте).
adb shell wm size 1080x1920
shot 09-main-short
adb shell wm size reset

# Узкий телефон (384 dp по ширине, как Galaxy S2x) в светлой теме: карты, шпаргалка, редактор, валюты.
adb shell cmd uimode night no
adb shell wm density 450
shot 11-main-narrow
shot 12-cheat-narrow --es screen cheat
WAIT=12 shot 13-editor-narrow --es screen editor
shot 14-currencies-narrow --es screen currencies
adb shell wm density reset
adb shell cmd uimode night yes

# Другие валюты трат: рупия Индонезии (курс ЦБ есть, у карт — оценка) и Шри-Ланки (у ЦБ курса нет).
shot 15-main-idr --es spend IDR
shot 16-main-lkr --es spend LKR
shot 17-currencies-idr --es spend IDR --es screen currencies
WAIT=12 shot 18-editor-lkr --es spend LKR --es screen editor
shot 19-cheat-lkr --es spend LKR --es screen cheat
shot 20-currencies-lkr --es spend LKR --es screen currencies
shot 21-settings --es screen settings
WAIT=12 shot 22-cash-editor --es screen editor --es card demo-aed-cash
# Знакомство по шагам: зачем, как считаем, что учитываем, откуда и куда, чем платите.
shot 23-onb-1-why --es screen onboarding --ei step 0
shot 24-onb-2-how --es screen onboarding --ei step 1
shot 25-onb-3-what --es screen onboarding --ei step 2
shot 26-onb-4-currencies --es screen onboarding --ei step 3
shot 27-onb-5-pay --es screen onboarding --ei step 4
# Справочник «Чем платить» (шторка ОАЭ), экраны главного «Карты» и «Поездка».
shot 30-guide --es screen guide
shot 31-page-cards --es screen cards
shot 33-page-trip --es screen trip
WAIT=10 shot 32-exchange --es screen exchange
# Наличные из банкомата: баты сняты с карты в юанях.
WAIT=12 shot 28-atm-editor --es spend THB --es screen editor --es card demo-thb-atm
shot 29-main-thb --es spend THB

# Скриншоты для RuStore (камера эмулятора показывает виртуальную комнату — её снимок делается на телефоне): 9:16 (1080×1920), тёмная тема, порядок как в карточке (docs/rustore/README.md).
adb shell cmd uimode night yes
adb shell wm size 1080x1920
WAIT=10 shot store-1-main
shot store-2-why --es screen onboarding --ei step 0
shot store-3-how --es screen onboarding --ei step 1
WAIT=12 shot store-4-editor --es screen editor
shot store-5-cheat --es screen cheat
adb shell cmd uimode night no
shot store-6-main-light
adb shell cmd uimode night yes
shot store-7-settings --es screen settings
adb shell wm size reset

# Первый запуск: данные стёрты — знакомство.
adb shell pm clear "$PKG"
adb shell am start -W -n "$PKG/$ACT" > /dev/null
sleep 10
adb exec-out screencap -p > "$OUT/10-onboarding.png"
echo "снимок: 10-onboarding"

ls -la "$OUT"
# Падения только нашего процесса: системные приложения эмулятора тоже иногда падают.
if adb logcat -d -b crash | grep -A1 'FATAL EXCEPTION' | grep -q "Process: $PKG,"; then
  echo "=== Падение при съёмке экранов ==="
  adb logcat -d -b crash -v raw
  exit 1
fi
