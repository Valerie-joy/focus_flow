import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Client-safe Supabase/Google config, read from local.properties (gitignored)
// rather than hardcoded — see SUPABASE_SETUP.md. Never put the Supabase
// *secret* key here; only values meant to ship inside the APK belong in this file.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProperties.getProperty(key) ?: ""

android {
    namespace = "com.focusflow"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.focusflow"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Without this, AGP falls back to android.test.InstrumentationTestRunner
        // — the pre-AndroidX runner, which does not understand
        // @RunWith(AndroidJUnit4::class). `connectedDebugAndroidTest` then
        // scanned the whole 118 MB APK classpath, found zero tests, and exited
        // *successfully*, so the suite appeared to pass while never running.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${localProp("SUPABASE_PUBLISHABLE_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("GOOGLE_WEB_CLIENT_ID")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Keep model files stored uncompressed in the APK so they can be
    // memory-mapped straight from the asset file descriptor instead of being
    // inflated into a heap copy on every open (itracker.tflite is 13.5 MB).
    androidResources {
        noCompress += listOf("tflite", "task")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            // Deliberately left off, not overlooked.
            //
            // R8 would help here (the APK carries MediaPipe, LiteRT and the
            // full Supabase/Ktor stack), but three of this app's dependencies
            // are exactly the kind R8 breaks quietly rather than loudly:
            //
            //  - kotlinx-serialization, which Supabase uses for every model,
            //    resolves serializers reflectively at runtime. Stripped
            //    `Companion.serializer()` methods fail at the first network
            //    call, not at build time.
            //  - MediaPipe's tasks-vision and LiteRT both reach native code
            //    through JNI, which R8 cannot see; the Java side of those
            //    bridges has to be kept explicitly.
            //  - Room's generated DAO implementations are looked up by name.
            //
            // None of those failures appear in a build log. They appear as a
            // release build that installs, opens, and then fails at sign-in or
            // when the gaze model loads. Verifying that needs a release build
            // run end to end on a physical device, which was not available
            // here — so enabling it would mean shipping an untested change to
            // the one build type users actually receive.
            //
            // To enable: turn both flags on, add keep rules for the four
            // libraries above, then exercise sign-in, an assessment with
            // tracking, and a PDF export on a real device.
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0") // provides the XML Theme.Material3.* styles used by AndroidManifest.xml
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation + ViewModel (Stage 1-5)
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // CameraX + ML Kit (Stage 4-5)
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    // MediaPipe Face Landmarker — 478 landmarks (468 face + 10 iris) and 52
    // blendshapes including the eyeLook* gaze set. Replaces ML Kit face
    // detection, which has no iris/pupil output at any setting and so could
    // only ever infer attention from head angle. Model lives in
    // app/src/main/assets/face_landmarker.task.
    implementation("com.google.mediapipe:tasks-vision:1.0.0")

    // LiteRT (TensorFlow Lite) runtime for the iTracker gaze-point model in
    // app/src/main/assets/itracker.tflite — see tools/itracker/README.md.
    // MediaPipe bundles its own TFLite statically inside its JNI library and
    // exposes no interpreter, so a separate runtime is needed. 1.4.x is the
    // classic org.tensorflow.lite.Interpreter API; the 2.x "CompiledModel" API
    // is a different surface and was not adopted. CPU/XNNPACK only: the GPU
    // delegate has no LRN kernel, so it would just partition the graph.
    implementation("com.google.ai.edge.litert:litert:1.4.2")

    // Room (Stage 8)
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // YouTube video playback for the assessment screen (WebView-based
    // wrapper around YouTube's IFrame Player API — Google's native
    // "YouTube Android Player API" is deprecated, this is the maintained
    // community-standard replacement)
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")

    // Supabase (Auth + Storage) — client-safe URL/anon key only, see
    // SUPABASE_SETUP.md and local.properties. BOM pins compatible versions
    // across all supabase-kt modules.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.7.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Native Google Sign-In via Credential Manager (feeds Supabase's
    // signInWith(IDToken) — see SUPABASE_SETUP.md for the Google Cloud setup)
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests (JVM only, no device/emulator required)
    testImplementation("junit:junit:4.13.2")

    // Compose UI tests (instrumented — these need a device or emulator).
    // Added for the auth/validation and results-rendering behaviours that can
    // only be checked against a real composition: focus order, IME actions,
    // error placement and the disabled/loading states of the submit button.
    // The BOM already pins compose-ui versions, so no versions are named here.
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // Supplies the empty activity the Compose test rule hosts content in.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
