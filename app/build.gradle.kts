import java.util.Properties

val telegramProps = Properties().apply {
    rootProject.file("telegram.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    buildFeatures { buildConfig = true }
    namespace = "com.example.dispatcher"
    compileSdk = 35

    // Подпись берётся из локального keystore.properties (в .gitignore, не публикуется).
    // Без него (публичная сборка) используется debug-подпись.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val hasKeystore = keystorePropsFile.exists()
    signingConfigs {
        create("release") {
            if (hasKeystore) {
                val props = Properties().apply {
                    keystorePropsFile.inputStream().use { load(it) }
                }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.dispatcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 278
        versionName = "1.52.4"
        multiDexEnabled = true
        buildConfigField("int", "TG_API_ID", telegramProps.getProperty("apiId", "0"))
        buildConfigField("String", "TG_API_HASH", "\"" + telegramProps.getProperty("apiHash", "").replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/versions/**",
                "META-INF/mailcap.default",
                "META-INF/mimetypes.default",
                "META-INF/javamail.charset.map",
                "META-INF/javamail.default.address.map",
                "META-INF/javamail.default.providers"
            )
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // pdfbox-android вызывает android.util.Log — в JVM-тестах эти вызовы заглушаются
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // офисные форматы: просмотр и редактирование docx/xlsx/pptx, извлечение текста из pdf
    implementation("org.apache.poi:poi-ooxml:5.2.5")
    // PDFBox-порт для Android: обычный PDFBox 3.x тянет java.awt (нет на Android) и падает
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // TDLib: только вход по телефону; prebuilt native AAR, без локальной сборки NDK
    implementation("io.github.tdlib-android:core:0.1.3")
    implementation("io.github.tdlib-android:ktx:0.1.3")
    // почта по IMAP (Яндекс/Gmail)
    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
