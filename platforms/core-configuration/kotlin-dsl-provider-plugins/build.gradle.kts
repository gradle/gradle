plugins {
    id("gradlebuild.distribution.implementation-kotlin")
}

description = "Kotlin DSL Provider Plugins"

dependencies {
    implementation(projects.kotlinDsl)

    implementation(projects.baseServices)
    implementation(projects.core)
    implementation(projects.coreApi)
    implementation(projects.functional)
    implementation(projects.fileCollections)
    implementation(projects.languageJvm)
    implementation(projects.logging)
    implementation(projects.pluginDevelopment)
    implementation(projects.pluginsJavaBase)
    implementation(projects.platformJvm)
    implementation(projects.resources)
    implementation(projects.snapshots)
    implementation(projects.toolingApi)
    implementation(projects.toolchainsJvm)

    implementation(libs.futureKotlin("scripting-compiler-impl-embeddable")) {
        isTransitive = false
    }
    implementation(libs.kotlinCompilerEmbeddable)

    implementation(libs.groovy)
    implementation(libs.slf4jApi)
    implementation(libs.inject)

    testImplementation(testFixtures(projects.kotlinDsl))
    testImplementation(libs.mockitoKotlin2)
}

packageCycles {
    excludePatterns.add("org/gradle/kotlin/dsl/provider/plugins/precompiled/tasks/**")
}
