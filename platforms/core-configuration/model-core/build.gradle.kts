plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.jmh")
}

description = "Implementation of configuration model types and annotation metadata handling (Providers, software model, conventions)"

gradlebuildJava {
    usesJdkInternals = true
}

dependencies {
    api(projects.serialization)
    api(projects.serviceLookup)
    api(projects.stdlibJavaExtensions)
    api(projects.coreApi)
    api(projects.problemsApi)
    api(projects.hashing)
    api(projects.baseServices)
    api(projects.files)
    api(projects.functional)
    api(projects.logging)
    api(projects.messaging)
    api(projects.persistentCache)
    api(projects.snapshots)

    api(libs.asm)
    api(libs.jsr305)
    api(libs.inject)
    api(libs.groovy)
    api(libs.guava)

    implementation(projects.baseServicesGroovy)
    implementation(projects.baseAsm)
    implementation(projects.serviceProvider)
    implementation(projects.serviceRegistryBuilder)

    implementation(libs.kotlinStdlib)
    implementation(libs.slf4jApi)
    implementation(libs.commonsLang)

    compileOnly(libs.errorProneAnnotations)

    testFixturesApi(testFixtures(projects.diagnostics))
    testFixturesApi(testFixtures(projects.core))
    testFixturesApi(projects.internalIntegTesting)
    testFixturesImplementation(projects.baseAsm)
    testFixturesImplementation(libs.guava)
    testFixturesImplementation(libs.groovyAnt)
    testFixturesImplementation(libs.groovyDatetime)
    testFixturesImplementation(libs.groovyDateUtil)

    testImplementation(projects.processServices)
    testImplementation(projects.fileCollections)
    testImplementation(projects.native)
    testImplementation(projects.resources)
    testImplementation(testFixtures(projects.coreApi))

    integTestImplementation(projects.platformBase)

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsNative) {
        because("ModelRuleCachingIntegrationTest requires a rules implementation")
    }

    jmhImplementation(platform(projects.distributionsDependencies))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API
}

integTest.usesJavadocCodeSnippets = true

packageCycles {
    excludePatterns.add("org/gradle/model/internal/core/**")
    excludePatterns.add("org/gradle/model/internal/inspect/**")
    excludePatterns.add("org/gradle/api/internal/tasks/**")
    excludePatterns.add("org/gradle/model/internal/manage/schema/**")
    excludePatterns.add("org/gradle/model/internal/type/**")
    excludePatterns.add("org/gradle/api/internal/plugins/*")
    // cycle between org.gradle.api.internal.provider and org.gradle.util.internal
    // (api.internal.provider -> ConfigureUtil, DeferredUtil -> api.internal.provider)
    excludePatterns.add("org/gradle/util/internal/*")
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
