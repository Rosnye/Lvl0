// Sincronización entre amigos sin servidor: identidad local, paquetes cifrados y transportes
// (proximidad con Nearby Connections, archivo compartido por cualquier mensajero).
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "io.gymtracker.sync"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":domain"))
    api(project(":crypto"))
    implementation(project(":data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.nearby)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
