import org.jetbrains.kotlin.gradle.plugin.KaptExtension

plugins {
    kotlin("jvm") version "2.4.0-Beta1"
    kotlin("kapt") version "2.4.0-Beta1"
}

repositories {
    mavenCentral()
}

// tag::cacheKapt[]
pluginManager.withPlugin("kotlin-kapt") {
    configure<KaptExtension> { useBuildCache = true }
}
// end::cacheKapt[]

// tag::fabricKotlin[]
apply(from = "fabric.gradle")
// end::fabricKotlin[]
