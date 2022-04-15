plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-compiler-embeddable")
}

description = "Kotlin Compiler Embeddable - patched for Gradle"

moduleIdentity.baseName.set("kotlin-compiler-embeddable-${libs.kotlinVersion}-patched-for-gradle")

dependencies {
    implementation(libs.futureKotlin("stdlib"))
    implementation(libs.futureKotlin("reflect"))
    implementation(libs.futureKotlin("script-runtime"))
    implementation(libs.futureKotlin("daemon-embeddable"))
    implementation(libs.jna)
    implementation(libs.trove4j)
}
