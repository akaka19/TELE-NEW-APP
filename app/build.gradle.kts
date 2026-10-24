plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.company.phonedirectory"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.company.phonedirectory"
        minSdk = 24
        targetSdk = 34
        versionCode = 11
        versionName = "1.0.11"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.linphone:linphone-sdk-android:5.4.128")
}
