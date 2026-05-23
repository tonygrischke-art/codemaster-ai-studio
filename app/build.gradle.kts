plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("com.google.dagger.hilt.android")
 id("com.google.devtools.ksp")
}

android {
 namespace = "com.codemaster.aistudio"
 compileSdk = 36

 defaultConfig {
 applicationId = "com.codemaster.aistudio"
 minSdk = 26
 targetSdk = 36
 versionCode = 10
 versionName = "2.0.0"

    testInstrumentationRunner = "com.codemaster.aistudio.HiltTestRunner"
 vectorDrawables {
 useSupportLibrary = true
 }

 // API keys - injected at build time via GitHub Actions secrets
 buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: ""}\"")
 buildConfigField("String", "KIMI_API_KEY", "\"${project.findProperty("KIMI_API_KEY") ?: ""}\"")
 }

 buildTypes {
 release {
 isMinifyEnabled = true
 isShrinkResources = true
 proguardFiles(
 getDefaultProguardFile("proguard-android-optimize.txt"),
 "proguard-rules.pro"
 )
 }
 debug {
 isDebuggable = true
 applicationIdSuffix = ".debug"
 }
 }

 compileOptions {
 sourceCompatibility = JavaVersion.VERSION_17
 targetCompatibility = JavaVersion.VERSION_17
 }

 kotlinOptions {
 jvmTarget = "17"
 freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
 }

 buildFeatures {
 compose = true
 buildConfig = true
 viewBinding = true
 }

 composeOptions {
 kotlinCompilerExtensionVersion = "1.5.14" // Kept as-is (stable version independent of compileSdk)
 }

 packaging {
 resources {
 excludes += "/META-INF/{AL2.0,LGPL2.1}"
 }
 }
}

dependencies {
 // Compose BOM updated to latest alpha for Android 16 compatibility
 implementation(platform("androidx.compose:compose-bom:2024.09.00-alpha"))
 implementation("androidx.compose.ui:ui:1.6.8")
 implementation("androidx.compose.ui:ui-graphics:1.6.8")
 implementation("androidx.compose.ui:ui-tooling-preview:1.6.8")
 implementation("androidx.compose.material3:material3:1.2.1")
 implementation("androidx.compose.material:material-icons-extended:1.6.8")
 implementation("androidx.compose.animation:animation:1.6.8")
 implementation("androidx.compose.foundation:foundation:1.6.8")

 // Navigation
 implementation("androidx.navigation:navigation-compose:2.7.7")

 // Hilt DI
 implementation("com.google.dagger:hilt-android:2.51.1")
 ksp("com.google.dagger:hilt-android-compiler:2.51.1")
 implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

 // Room Database
 implementation("androidx.room:room-runtime:2.6.1")
 implementation("androidx.room:room-ktx:2.6.1")
 ksp("androidx.room:room-compiler:2.6.1")

 // Networking
 implementation("com.squareup.retrofit2:retrofit:2.9.0")
 implementation("com.squareup.retrofit2:converter-gson:2.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

 // Coroutines
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

 // DataStore
 implementation("androidx.datastore:datastore-preferences:1.0.0")

 // Lifecycle ViewModel
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

 // Splash Screen
 implementation("androidx.core:core-splashscreen:1.0.1")
 implementation("androidx.webkit:webkit:1.9.0")
 // Kotlin serialization
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
 // Coil for image loading
 implementation("io.coil-kt:coil-compose:2.6.0")
 // WorkManager
 implementation("androidx.work:work-runtime-ktx:2.9.0")
 // Security crypto
 implementation("androidx.security:security-crypto:1.1.0-alpha06")
 // Zip support
 implementation("org.apache.commons:commons-compress:1.26.1")
 // DocumentFile
 implementation("androidx.documentfile:documentfile:1.0.1")

 // Termux libraries from JitPack
 implementation("com.github.termux.termux-app:terminal-emulator:v0.118.1")
 implementation("com.github.termux.termux-app:terminal-view:v0.118.1")

 // Testing
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.1.5")
 androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
 androidTestImplementation("androidx.test:core:1.5.0")
 androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
 ksp("com.google.dagger:hilt-compiler:2.51.1")
 implementation("com.google.dagger:hilt-android-compiler:2.51.1")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
 debugImplementation("androidx.compose.ui:ui-tooling:1.3.0")
 debugImplementation("androidx.compose.ui:ui-test-manifest:1.3.0")

 // Material Components for XML layouts
 implementation("com.google.android.material:material:1.12.0")
}