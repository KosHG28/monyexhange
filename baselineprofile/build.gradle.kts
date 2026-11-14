plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.dirhamrub.baselineprofile"
    compileSdk = 37
    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
}

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
}
