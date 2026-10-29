# Решения

Короткие записи о том, что не было однозначно задано в SPEC, и почему выбрано так.

## Сборка
- **AGP 9.4.0, Gradle 9.8.2, Kotlin 2.4.21, Compose BOM 2026.09.00, CameraX 1.6.2, compileSdk/targetSdk 37** — последние стабильные на 29.10.2026. В AGP 9 Kotlin встроен, поэтому плагин `org.jetbrains.kotlin.android` не подключается; версию Kotlin задаёт плагин Compose Compiler.
- **ML Kit `text-recognition` 16.0.1** — bundled-модель латиницы (работает офлайн).
- **Проверка сборки в GitHub Actions.** В облачном окружении закрыт `dl.google.com` (Android SDK и Google Maven), поэтому Android-часть собирается только в CI. Чистая логика (`domain/`) дополнительно проверяется локально харнессом `tools/domain-check`.
- **В CI собираются и debug, и release APK.** В релиз (GitHub Release) идёт release-сборка: она с R8 и заметно быстрее debug-сборки Compose, что важно для камеры. Обе подписаны одним ключом и имеют один applicationId, так что ставятся поверх друг друга.
- **versionCode = номер запуска CI** (`GITHUB_RUN_NUMBER`), чтобы каждое следующее APK ставилось как обновление. Локальная сборка получает 1.
- **Только `arm64-v8a` и `armeabi-v7a`** — ML Kit тянет нативные библиотеки; x86 на телефоне не нужен, APK меньше.
- **Ключ подписи** `keystore/app.keystore` (PKCS12, alias `dirhamrub`, пароль `dirhamrub`) лежит в репозитории — приложение личное, так задано в SPEC.
