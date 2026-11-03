import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Logika bisnis tanpa ketergantungan Android. Target iOS ditambahkan saat aplikasi iOS dimulai.
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
