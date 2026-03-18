plugins {
    id("gradlebuild.build-logic.kotlin-dsl-gradle-plugin")
}

description = "Provides plugins to configure Kotlin DSL and patch the Kotlin compiler for use in Kotlin subprojects"

dependencies {
    implementation("gradlebuild:basics")

    implementation(projects.dependencyModules)
    implementation(projects.jvm)
    implementation(projects.kotlinDslSharedRuntime)

    implementation(buildLibs.kgp)
    implementation(buildLibs.kotlinSamWithReceiver)
    implementation(libs.asm)
    implementation(buildLibs.qdox)

    testImplementation(testLibs.junit)
    testImplementation(testLibs.mockitoKotlin)
}
