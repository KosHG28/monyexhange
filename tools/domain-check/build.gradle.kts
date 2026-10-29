plugins {
    kotlin("jvm") version "2.4.21"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val app = rootDir.resolve("../../app/src")

sourceSets {
    main {
        kotlin.srcDir(app.resolve("main/java/app/dirhamrub/domain"))
    }
    test {
        kotlin.srcDir(app.resolve("test/java/app/dirhamrub/domain"))
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
