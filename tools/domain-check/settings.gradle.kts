// Отдельная JVM-сборка только для domain-кода приложения: позволяет гонять его тесты
// там, где нет Android SDK. Исходники берутся прямо из app/src, копий нет.
pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        mavenCentral()
    }
}
rootProject.name = "domain-check"
