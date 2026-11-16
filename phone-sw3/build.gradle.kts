plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Phone half of the SW3 build: relays the watch's HA WebSocket over the Wear Data Layer. Same package and
// signing key as wear-legacy (the Data Layer only connects apps that share both); the HA token lives only here.
android {
    namespace = "com.mau.hub.wear.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mau.hub.wear.sw3"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].java.srcDir("../sw3common") // relay paths + chunking, shared with the watch
    // homeUrl comes from MauHomeAssist's config (same home)
    sourceSets["main"].assets.srcDir("../../ha-companion-revamped-public/mauwatch/app/src/main/assets")
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}

// the SW3's phone half, released with wear-legacy (not the Fossil)
base { archivesName.set("joint-launcher-sw3-phone") }
