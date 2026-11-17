plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Android Wear 1.5 build (Sony SWR50: API 23, 320x320 square): the flip-clock face plus MauHomeAssist's screens in plain
// Compose (no Wear Compose/Tiles/androidx.wear, which need newer Android); HA through the phone (phone-sw3)
android {
    namespace = "com.mau.hub.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mau.hub.wear"
        minSdk = 23
        targetSdk = 34
        versionCode = 7
        versionName = "0.7-legacy"
        applicationIdSuffix = ".sw3" // its own package, never confused with the Fossil build
        // the TV's entities for the face's Home/Back/volume keys, from the Fossil launcher's config (git-ignored)
        val launcher = file("../app/src/main/assets/config.json").takeIf { it.exists() }
            ?.let { groovy.json.JsonSlurper().parse(it) as Map<*, *> }.orEmpty()
        buildConfigField("String", "TV", "\"${launcher["tv"] ?: ""}\"")
        buildConfigField("String", "TV_REMOTE", "\"${launcher["tvRemote"] ?: ""}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        // personal sideload: shrunk (icons-extended is big, and installs go over Bluetooth), signed with the debug key
        // like phone-sw3's debug build, since the Data Layer only connects apps with the same key
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets["main"].java.srcDir("../sw3common") // relay paths + chunking, shared with phone-sw3
    sourceSets["main"].assets.srcDir("../../ha-companion-revamped-public/mauwatch/app/src/main/assets") // MauHomeAssist's config.json
}

dependencies {
    implementation("com.google.android.support:wearable:2.9.0")
    compileOnly("com.google.android.wearable:wearable:2.9.0")
    // the 2016 Data Layer API: the SW3's Play services is 8.7 (as Joint Remote's wear-legacy found)
    implementation("com.google.android.gms:play-services-wearable:8.4.0")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // Android's org.json is a stub in JVM unit tests
}

// the SW3 (square, Android Wear 1.5) release; the Fossil (round) is :app, built and versioned apart, sharing no code or resources
base { archivesName.set("joint-launcher-sw3") }
