# CLAUDE.md

Личное Android-приложение «Дирхамы в рубли»: калькулятор AED → ₽ по курсу из банковских чеков
(карты в тенге и в юанях) и AR-камера, рисующая рубли поверх ценников.
Требования — `docs/SPEC.md` (источник истины), план — `docs/PLAN.md`, принятые решения — `docs/DECISIONS.md`.

## Команды

```bash
./gradlew testDebugUnitTest            # unit-тесты (domain)
./gradlew lintDebug                    # lint, ошибки валят сборку
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease              # R8 + тот же ключ подписи
./gradlew testDebugUnitTest assembleDebug lintDebug   # перед каждым коммитом

# Без Android SDK (облачное окружение): только domain-код и его тесты на чистой JVM
./gradlew -p tools/domain-check test
```

CI: `.github/workflows/android.yml` — на каждый push тесты, lint, debug и release APK (artifact
`dirhamrub-apk`); на тег `v*` — GitHub Release с `dirhamrub-vX.Y.Z.apk`. Если тег не запушить
(в облачном окружении пуш тегов запрещён), релиз создаёт ручной запуск workflow с `release=vX.Y.Z`.

## Архитектура

Один модуль `:app`, один `MainActivity`, Compose + Material 3 со своей темой, MVVM без DI.

```
app/src/main/java/app/dirhamrub/
  domain/            чистый Kotlin, без android.* — всё покрыто тестами
    Card, Receipt, CardRate, RateMath     курс карты из двух чеков
    Numbers                               разбор ввода и форматирование ru-RU
    Converter                             AED ⇄ ₽ ⇄ валюта карты, сравнение карт
    Calculator                            выражение «22 + 15,5 + 8»
    price/PriceParser                     цены из OCR-строк (скоринг, фильтры, старая цена)
    price/PriceTracker                    стабилизация меток между кадрами
  data/              DataStore (SettingsRepository)
  ui/theme/          цвета, шрифты, тема
  ui/main/           главный экран + MainViewModel
  ui/rate/           шторка «Курс»
  ui/cheat/          шпаргалка
  ui/camera/         AR-камера: CameraX + ML Kit, оверлей на Canvas
```

Поток данных: `SettingsRepository` (DataStore) → `MainViewModel` (`StateFlow<MainUiState>`) → экраны.
Камера: `MlKitAnalyzer` (VIEW_REFERENCED, фоновый executor) → `OcrMapper` → `PriceParser` →
`PriceTracker` → готовая раскладка меток → `StateFlow` → Canvas (в draw ничего не создаётся).

## Правила

- Деньги и курсы — только `BigDecimal` с `MathContext.DECIMAL64`; округление `HALF_UP` только при показе
  (функции в `domain/Numbers.kt`). Никаких `Double` в расчётах курса.
- `domain/` не импортирует `android.*` и `androidx.*` — его тесты должны идти на чистой JVM.
- Любое изменение математики, форматирования или `PriceParser` — с unit-тестом.
- Весь интерфейс на русском; строки — в `res/values/strings.xml`.
- Цвета только из `ui/theme` (`AppColors`), не хардкодить в экранах (исключение — «банковские» градиенты карт).
- Цифры — tabular figures (`fontFeatureSettings = "tnum"`), моноширинные — JetBrains Mono.
- Нажимаемые элементы ≥ 48 dp и с `contentDescription`/семантикой для TalkBack.
- Анимации через `LocalReduceMotion`: при отключённых системных анимациях — без них.
- Главный экран не вызывает системную клавиатуру; она есть только в шторке «Курс».
- Сети нет и не нужно: не добавлять разрешение INTERNET.
- Новые неочевидные решения — строкой в `docs/DECISIONS.md`.
- Коммит на этап, перед коммитом — тесты + lint + сборка (локально или в CI).
