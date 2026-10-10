import java.io.FileOutputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 版本号从 version.properties 读取；共享工作区通过本地协调工具同步。
val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
// A local workspace version authority prevents building an unsynchronised version.
val workspaceVersionFile = rootProject.file("../PROJECT_VERSION.properties")
if (workspaceVersionFile.isFile) {
    val workspaceVersion = Properties().apply { workspaceVersionFile.inputStream().use { load(it) } }
    for (key in listOf("versionMajor", "versionMinor", "versionPatch", "versionCode")) {
        check(versionProps.getProperty(key) == workspaceVersion.getProperty(key)) {
            "版本未同步，请先运行本地版本同步工具。"
        }
    }
}
val appVersionMajor: String = versionProps.getProperty("versionMajor", "1")
val appVersionMinor: String = versionProps.getProperty("versionMinor", "1")
val appVersionPatch: String = versionProps.getProperty("versionPatch", "0")
val appVersionCode: Int = versionProps.getProperty("versionCode", "1").toIntOrNull() ?: 1
val appVersionName: String = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

android {
    namespace = "io.wenyou.textquest"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.wenyou.textquest"
        // Android 6.0 – 16; Java APIs newer than the device come from core library desugaring.
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // Share codes record which app wrote them; the link page opens this app through its own scheme.
        buildConfigField("String", "SHARE_ORIGIN", "\"hx\"")
        buildConfigField("String", "SHARE_SCHEME", "\"hongxu\"")
        manifestPlaceholders["shareScheme"] = "hongxu"
        buildConfigField("String", "APP_FILE_PREFIX", "\"HongXu\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "content"
    productFlavors {
        create("alpha") {
            buildConfigField("String", "UPDATE_REPOSITORY", "\"wangmikuwang/HongXu-Android\"")
            dimension = "content"
            applicationIdSuffix = ".alpha"

        }
    }

    buildTypes {
        release {
            // Distributed builds: R8 + resource shrinking, signed with the key already used by published APKs.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// APK 产物去掉 debug 字样，直接可用于分发。
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)?.outputFileName?.set("HongXu-v$appVersionName.apk")
        }
    }
}

// 版本号按 x.yy.zz 规则递增：
//   patch（默认，仅 bug 修复）→ zz+1；zz 每满 100 进位到 yy 并归零 zz
//   minor（新功能/重大变化）  → yy+1 且 zz=0；yy 每满 10 进位到 xx 并归零 yy
//   major（重大架构/巨大功能） → xx+1 且 yy=zz=0
// 用法：gradlew bumpVersion              （bug 修复）
//       gradlew bumpVersion -Pbump=minor （新功能）
//       gradlew bumpVersion -Pbump=major （重大变化）
tasks.register("bumpVersion") {
    doLast {
        check(!workspaceVersionFile.isFile) { "此工作区使用统一版本管理，请运行 tools/sync_app_versions.py。" }
        val f = rootProject.file("version.properties")
        val p = Properties().apply { f.inputStream().use { load(it) } }
        var major = p.getProperty("versionMajor", "1").toIntOrNull() ?: 1
        var minor = p.getProperty("versionMinor", "1").toIntOrNull() ?: 1
        var patch = p.getProperty("versionPatch", "0").toIntOrNull() ?: 0
        val code = (p.getProperty("versionCode", "1").toIntOrNull() ?: 1) + 1
        val step = (project.findProperty("bump") as? String)?.trim()?.lowercase() ?: "patch"
        when (step) {
            "major" -> { major++; minor = 0; patch = 0 }
            "minor" -> { minor++; patch = 0 }
            else -> { patch++ }
        }
        if (patch > 99) { minor += patch / 100; patch %= 100 }
        if (minor > 9) { major += minor / 10; minor %= 10 }
        p["versionMajor"] = major.toString()
        p["versionMinor"] = minor.toString()
        p["versionPatch"] = patch.toString()
        p["versionCode"] = code.toString()
        FileOutputStream(f).use { p.store(it, "WenYou version x.yy.zz; run 'gradlew bumpVersion(-Pbump=patch|minor|major)' then assemble") }
        println("已升版 -> $major.$minor.$patch（versionCode=$code，step=$step）")
    }
}

dependencies {
    compileOnly(libs.error.prone.annotations)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
