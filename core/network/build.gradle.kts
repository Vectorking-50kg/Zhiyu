import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Public OAuth client identifier for the project's GitHub device authorization.
val githubCopilotClientId = providers.gradleProperty("zhiyu.githubCopilotClientId")
    .orElse("Ov23likb98EbcFlQMBsQ").get().trim()
require(githubCopilotClientId.matches(Regex("[A-Za-z0-9._]*"))) { "Invalid GitHub OAuth Client ID" }

// Installed-app registration is supplied by the release environment or ignored local settings.
val localOAuthProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun googleOAuthValue(property: String, environment: String): String = providers.gradleProperty(property)
    .orElse(providers.environmentVariable(environment))
    .orElse(localOAuthProperties.getProperty(property).orEmpty()).get().trim()
val antigravityClientId = googleOAuthValue("zhiyu.antigravityClientId", "ANTIGRAVITY_OAUTH_CLIENT_ID")
val antigravityClientSecret = googleOAuthValue("zhiyu.antigravityClientSecret", "ANTIGRAVITY_OAUTH_CLIENT_SECRET")
require(listOf(antigravityClientId, antigravityClientSecret).all { it.matches(Regex("[A-Za-z0-9._-]*")) }) {
    "Invalid Antigravity OAuth build configuration"
}
require(antigravityClientId.isEmpty() == antigravityClientSecret.isEmpty()) {
    "Configure both Antigravity OAuth client values"
}

android {
    namespace = "funapp.ctrlcv.zhiyu.core.network"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "GITHUB_COPILOT_CLIENT_ID", "\"$githubCopilotClientId\"")
        buildConfigField("String", "ANTIGRAVITY_OAUTH_CLIENT_ID", "\"$antigravityClientId\"")
        buildConfigField("String", "ANTIGRAVITY_OAUTH_CLIENT_SECRET", "\"$antigravityClientSecret\"")
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:storage"))

    implementation(libs.core.ktx)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.coroutines.core)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
