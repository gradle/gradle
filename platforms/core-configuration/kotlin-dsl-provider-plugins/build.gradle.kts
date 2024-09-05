plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Provider Plugins"

dependencies {
    api(projects.baseServices)
    api(projects.classloading)
    api(projects.core)
    api(projects.coreApi)
    api(projects.kotlinDsl)
    api(projects.logging)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)

    api(libs.inject)
    api(libs.kotlinStdlib)

    implementation(projects.concurrent)
    implementation(projects.functional)
    implementation(projects.fileCollections)
    implementation(projects.hashing)
    implementation(projects.jvmServices)
    implementation(projects.loggingApi)
    implementation(projects.pluginDevelopment)
    implementation(projects.pluginsJavaBase)
    implementation(projects.platformJvm)
    implementation(projects.resources)
    implementation(projects.serviceLookup)
    implementation(projects.snapshots)
    implementation(projects.toolingApi)
    implementation(projects.toolchainsJvm)
    implementation(projects.toolchainsJvmShared)

    implementation(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(libs.kotlinCompilerEmbeddable)
    implementation(libs.slf4jApi)

    compileOnly(libs.kotlinReflect)

    testImplementation(testFixtures(projects.kotlinDsl))
    testImplementation(libs.mockitoKotlin2)
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/provider/plugins/precompiled/tasks/**")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
