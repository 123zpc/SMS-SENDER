plugins {
    id("com.android.application")
}

android {
    namespace = "com.smsagent"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.smsagent"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
}
