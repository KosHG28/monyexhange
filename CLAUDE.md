# CLAUDE.md

«Курс по чеку» (бывшие «Дирхамы в рубли»): калькулятор для путешественников. Пользователь заводит свои
карты в любых валютах и чеки (пополнение, покупки); приложение считает реальный курс каждой карты для
валюты трат → итоговой валюты, сверяет с курсом ЦБ РФ и рисует суммы поверх ценников в AR-камере.
Исходная спецификация — `docs/SPEC.md` (версия 0.x), план — `docs/PLAN.md`, решения — `docs/DECISIONS.md`
(раздел «Курс по чеку 1.0» описывает универсальную версию).

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
`dirhamrub-apk`), дымовой тест камеры и скриншоты экранов на эмуляторе (пререлиз «screens»:
`tools/screens.sh`, демо-данные — extra `demo` в отладочной сборке, экран — extra `screen`); на тег `v*` — GitHub Release с `dirhamrub-vX.Y.Z.apk`. Если тег не запушить
(в облачном окружении пуш тегов запрещён), релиз создаёт ручной запуск workflow с `release=vX.Y.Z`.
Ещё: «Перечень валют ЦБ» (`.github/workflows/cbr-list.yml`, по понедельникам) — `tools/cbr-check.py` сверяет
валюты из ответа ЦБ с `Currency.cbr`; Dependabot (`.github/dependabot.yml`) — еженедельные PR с обновлениями.
Подпись: ключ только из секретов репозитория (`KEYSTORE_BASE64` — ключ в base64, `SIGNING_*` — пароли); без них
(pull request'ы Dependabot, локальная сборка) — отладочный ключ, и выпуск CI отменяет.

## Архитектура

Один модуль `:app`, один `MainActivity`, Compose + Material 3 со своей темой, MVVM без DI.

```
app/src/main/java/app/dirhamrub/
  domain/            чистый Kotlin, без android.* — всё покрыто тестами
    Numbers, Calculator                   разбор и форматирование ru-RU, выражение «22 + 15,5», Line
    Settings                              настройки приложения и когда скачивать курсы (RatesPolicy)
    wallet/Currency, Countries            ~150 валют и ~240 стран — генерируются tools/currencies/gen.py из CLDR
    wallet/CurrencySearch, QuickSums      поиск страны/валюты (и курортов), быстрые суммы и шпаргалка от размера валюты
    wallet/Wallet                         карты, чеки пополнения/покупок, ручные курсы
    wallet/RateEngine                     курс карты, наценка к ЦБ, лучшая карта
    wallet/OfficialRates, CbrParser       курсы ЦБ РФ (XML и JSON-зеркало), справочный курс с UnionPay
    wallet/UnionPay                       курсы UnionPay: разбор файла, валюты расчёта
    wallet/WalletCodec                    хранение кошелька и курсов (Properties)
    price/PriceParser, PriceCurrency      цены из OCR-строк для каждой валюты трат
    price/PriceTracker                    стабилизация меток между кадрами
  data/              WalletRepository (DataStore + перенос с 0.x), CbrClient и UnionPayClient (сеть)
  ui/theme/          цвета, шрифты, тема, оформления карт
  ui/main/           единственный главный экран (MainScreen: итог, поле цены, карточки способов, курс ЦБ;
                     Keypad 4 × 5; PlaceSheet — шторка страны; Banners, EmptyRate) + MainViewModel
  ui/money/          «Карты и наличные» (из настроек): все способы оплаты
  ui/guide/          справочник «Чем платить в стране»
  ui/onboarding/     знакомство при первом запуске
  ui/cards/          редактор карты и чеков
  ui/currency/       выбор валюты трат и итоговой
  ui/cheat/          шпаргалка
  ui/settings/       настройки: карты и наличные, курсы (когда и откуда), обновление, UnionPay, камера, тема
  ui/components/     бумажный чек, поля, выбор валюты
  ui/camera/         AR-камера: CameraX + ML Kit, оверлей на Canvas
```

Поток данных: `WalletRepository` (DataStore) + `CbrClient` → `MainViewModel` (`StateFlow<MainUiState>`,
`RateEngine.quoteAll`) → экраны.
Камера: `MlKitAnalyzer` (VIEW_REFERENCED, фоновый executor) → `OcrMapper` → `PriceParser` →
`PriceTracker` → готовая раскладка меток → `StateFlow` → Canvas (в draw ничего не создаётся).

## Правила

- Деньги и курсы — только `BigDecimal` с `MathContext.DECIMAL64`; округление `HALF_UP` только при показе
  (функции в `domain/Numbers.kt`). Никаких `Double` в расчётах курса.
- `domain/` не импортирует `android.*` и `androidx.*` — его тесты должны идти на чистой JVM.
- Любое изменение математики, форматирования или `PriceParser` — с unit-тестом.
- Весь интерфейс на русском; строки — в `res/values/strings.xml` (названия валют — в `Currency`).
- Валюта рядом с суммой — `Currency.mark`: знак только у ₽, $, €, ¥ (юань), остальные — кодом («10 000 AMD»);
  `symbol` — для поиска, распознавания ценников и подписи валюты страны в шторке ⓘ.
- Цвета только из `ui/theme` (`AppColors`), не хардкодить в экранах (исключение — «банковские» градиенты карт).
  Синий `accent` — ввод и действия, `good` — выгода, `danger` — только ошибки и удаление.
- Цифры — tabular figures (`fontFeatureSettings = "tnum"`), один шрифт — Inter Tight (`ui/theme/Type.kt`), суммы тонким начертанием.
- Нажимаемые элементы ≥ 48 dp и с `contentDescription`/семантикой для TalkBack.
- Анимации через `LocalReduceMotion`: при отключённых системных анимациях — без них.
- Главный экран не вызывает системную клавиатуру; она есть только в шторке «Курс».
- Сеть — только для курсов ЦБ (`CbrClient`) и UnionPay (`UnionPayClient`); всё остальное работает офлайн.
- Новые неочевидные решения — строкой в `docs/DECISIONS.md`.
- Коммит на этап, перед коммитом — тесты + lint + сборка (локально или в CI).
