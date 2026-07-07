plugins {
    id("com.android.application")
}

android {
    namespace = "com.smsagent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.smsagent"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0"
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
