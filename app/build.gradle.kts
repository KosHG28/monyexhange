plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    // Пакет в коде прежний (app.dirhamrub), идентификатор приложения — ru.kurspocheku (с 1.18, новый ключ).
    namespace = "app.dirhamrub"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.kurspocheku"
        minSdk = 26
        targetSdk = 37
        // В CI номер сборки растёт, поэтому каждое новое APK ставится поверх старого.
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "1.42.1"
        ndk {
            // Только 64-битные телефоны: копия распознавания текста для 32-битных весила 6,8 МБ из 23.
            abiFilters += listOf("arm64-v8a")
            // Для прогона на x86_64-эмуляторе в CI: ./gradlew assembleRelease -Pdirhamrub.x86=true
            if (providers.gradleProperty("dirhamrub.x86").isPresent) abiFilters += "x86_64"
        }
    }

    // Ключ подписи — только из секретов репозитория (CI: SIGNING_KEYSTORE_FILE и пароли, см. android.yml),
    // один и тот же — обновления ставятся поверх. Без них (pull request'ы Dependabot, локальная сборка) —
    // отладочный ключ Android: такой APK поверх установленного не встанет, и выпуск CI без ключа не делает.
    fun secret(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotEmpty() }
    val keystore = secret("SIGNING_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    val keystorePassword = secret("SIGNING_STORE_PASSWORD")
    val signingAlias = secret("SIGNING_KEY_ALIAS")
    val signingKeyPassword = secret("SIGNING_KEY_PASSWORD")
    signingConfigs {
        if (keystore != null && keystorePassword != null && signingAlias != null && signingKeyPassword != null) {
            create("app") {
                storeFile = keystore
                storeType = "pkcs12"
                storePassword = keystorePassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    val signing = signingConfigs.findByName("app") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            signingConfig = signing
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        // Версии библиотек обновляем осознанно, а не по подсказке линтера.
        disable += listOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Только отладочная сборка: проверка Gemini Nano (src/debug/…/nanolab). В релиз не попадает.
    debugImplementation(libs.mlkit.genai.prompt)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
