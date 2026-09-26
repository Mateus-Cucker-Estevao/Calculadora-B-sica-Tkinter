// Configuração do módulo "app" (o aplicativo em si).
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mateus.avaliadorcorridas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mateus.avaliadorcorridas"
        minSdk = 29 // Android 10
        targetSdk = 35
        versionCode = 6
        versionName = "1.5"

        // Só processadores de celular (deixa o APK menor por causa do ML Kit).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        // Chave de assinatura fixa, guardada no próprio repositório (app/debug.keystore).
        // Assim o APK gerado no GitHub e o gerado no seu Android Studio têm a mesma
        // assinatura, e você pode instalar versões novas por cima sem desinstalar.
        // Uso pessoal apenas — nunca use essa chave para publicar na Play Store.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Reconhecimento de texto em imagens, 100% offline (modelo embutido no APK). Usado no plano B.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
