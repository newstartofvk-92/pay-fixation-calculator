import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class ValidateReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val hasStorePath: Property<Boolean>

    @get:Input
    abstract val hasStorePassword: Property<Boolean>

    @get:Input
    abstract val hasKeyPassword: Property<Boolean>

    @get:Input
    abstract val keystoreFileExists: Property<Boolean>

    @get:Input
    abstract val keyAlias: Property<String>

    @TaskAction
    fun validate() {
        val missingValues = mutableListOf<String>()
        if (!hasStorePath.get()) {
            missingValues += "PAYFIXATION_STORE_FILE or keystore.properties:storeFile"
        }
        if (!hasStorePassword.get()) {
            missingValues += "PAYFIXATION_STORE_PASSWORD or keystore.properties:storePassword"
        }
        if (!hasKeyPassword.get()) {
            missingValues += "PAYFIXATION_KEY_PASSWORD or keystore.properties:keyPassword"
        }
        if (missingValues.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Provide: ${missingValues.joinToString()}."
            )
        }
        if (!keystoreFileExists.get()) {
            throw GradleException(
                "The configured release keystore file was not found. Check " +
                    "PAYFIXATION_STORE_FILE or keystore.properties:storeFile."
            )
        }
        if (keyAlias.get() != "key0") {
            throw GradleException("Release signing must use the registered upload key alias.")
        }
    }
}

val releaseSigningProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.isFile) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun releaseSigningValue(environmentName: String, propertyName: String): String? =
    providers.environmentVariable(environmentName).orNull?.takeIf { it.isNotBlank() }
        ?: releaseSigningProperties.getProperty(propertyName)?.takeIf { it.isNotBlank() }

val releaseStorePath = releaseSigningValue("PAYFIXATION_STORE_FILE", "storeFile")
val releaseStorePassword = releaseSigningValue("PAYFIXATION_STORE_PASSWORD", "storePassword")
val releaseKeyPassword = releaseSigningValue("PAYFIXATION_KEY_PASSWORD", "keyPassword")
val releaseStoreFile = releaseStorePath?.let { file(it) }
val registeredUploadKeyAlias = "key0"

val validateReleaseSigning = tasks.register<ValidateReleaseSigningTask>("validateReleaseSigning") {
    group = "verification"
    description = "Checks that the registered upload key is configured for release builds."
    hasStorePath.set(!releaseStorePath.isNullOrBlank())
    hasStorePassword.set(!releaseStorePassword.isNullOrBlank())
    hasKeyPassword.set(!releaseKeyPassword.isNullOrBlank())
    keystoreFileExists.set(releaseStoreFile?.isFile == true)
    keyAlias.set(registeredUploadKeyAlias)
}

tasks.configureEach {
    if (name == "preReleaseBuild") {
        dependsOn(validateReleaseSigning)
    }
}

android {
    namespace = "com.niyammitra.payfixationcalculator"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.niyammitra.payfixationcalculator"
        minSdk = 24
        targetSdk = 37
        // Increment the version code for the next Google Play test release.
        // Play Console requires every uploaded app bundle to use a new version code.
        versionCode = 3
        // Version 1.2 identifies this test build as the third app release.
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storeType = "PKCS12"
            keyAlias = registeredUploadKeyAlias
            storePassword = releaseStorePassword
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.play.services.ads)
    implementation(libs.play.billing)
    implementation(libs.ump)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
