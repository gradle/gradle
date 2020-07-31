plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.kotlin-dsl-compiler-embeddable")
}

description = "Kotlin Compiler Embeddable - patched for Gradle"

moduleIdentity.baseName.set("kotlin-compiler-embeddable-${libs.kotlinVersion}-patched-for-gradle")

dependencies {
    api(libs.futureKotlin("stdlib"))
    api(libs.futureKotlin("reflect"))
    api(libs.futureKotlin("script-runtime"))
    api(libs.futureKotlin("daemon-embeddable"))

    runtimeOnly(libs.trove4j)
}
