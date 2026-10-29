plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.dirhamrub"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.dirhamrub"
        minSdk = 26
        targetSdk = 37
        // В CI номер сборки растёт, поэтому каждое новое APK ставится поверх старого.
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "1.1.0"
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
            // Для прогона на x86_64-эмуляторе в CI: ./gradlew assembleRelease -Pdirhamrub.x86=true
            if (providers.gradleProperty("dirhamrub.x86").isPresent) abiFilters += "x86_64"
        }
    }

    signingConfigs {
        // Личное приложение: стабильный ключ лежит в репозитории, чтобы обновления ставились поверх.
        create("app") {
            storeFile = rootProject.file("keystore/app.keystore")
            storeType = "pkcs12"
            storePassword = "dirhamrub"
            keyAlias = "dirhamrub"
            keyPassword = "dirhamrub"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("app")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("app")
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

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
