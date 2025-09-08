plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Provider Plugins"

dependencies {
    api(projects.classloaders)
    api(projects.core)
    api(projects.coreApi)
    api(projects.kotlinDsl)
    api(projects.logging)
    api(projects.serviceProvider)
    api(projects.stdlibJavaExtensions)
    api(projects.persistentCache)
    api(projects.declarativeDslToolingModels)
    api(projects.softwareFeatures)

    api(libs.inject)
    api(libs.kotlinStdlib)

    implementation(projects.baseServices)
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
    implementation(projects.toolingApi)
    implementation(projects.toolchainsJvm)
    implementation(projects.toolchainsJvmShared)
    implementation(projects.declarativeDslEvaluator)
    implementation(projects.declarativeDslProvider)
    implementation(projects.declarativeDslCore)

    implementation(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(libs.kotlinCompilerEmbeddable)
    implementation(libs.slf4jApi)

    compileOnly(libs.kotlinReflect)

    testImplementation(testFixtures(projects.kotlinDsl))
    testImplementation(libs.mockitoKotlin)
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/provider/plugins/precompiled/tasks/**")
}

// Kotlin DSL provider plugins should not be part of the public API
// TODO Find a way to not register this and the task instead
configurations.remove(configurations.apiStubElements.get())
