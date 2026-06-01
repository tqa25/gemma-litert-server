plugins {
    id("com.android.application")
}

android {
    namespace = "dev.gemma.androidbackend"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.gemma.androidbackend"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
