plugins {
    id("gradlebuild.distribution.api-java")
}

description = """JVM-specific testing functionality, including the Test type and support for configuring options for and detecting
tests written in various JVM testing frameworks. This project "extends" the testing-base project by sub-typing many
of its abstractions with JVM-specific abstractions or implementations.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build, and is a necessary
dependency for any projects working directly with Test tasks.
"""

dependencies {
    api(projects.baseServices)
    api(projects.buildOperations)
    api(projects.buildProcessServices)
    api(projects.core)
    api(projects.coreApi)
    api(projects.fileOperations)
    api(projects.jvmServices)
    api(projects.logging)
    api(projects.messaging)
    api(projects.modelCore)
    api(projects.reporting)
    api(projects.stdlibJavaExtensions)
    api(projects.testingBase)
    api(projects.testingBaseInfrastructure)
    api(projects.time)
    api(projects.toolchainsJvm)
    api(projects.toolchainsJvmShared)

    api(libs.asm)
    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.inject)
    api(libs.jspecify)

    implementation(projects.classloaders)
    implementation(projects.concurrent)
    implementation(projects.serviceLookup)
    implementation(projects.fileTemp)
    implementation(projects.functional)
    implementation(projects.loggingApi)
    implementation(projects.platformBase)
    implementation(projects.testingJvmInfrastructure)

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.junit)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(projects.core))
    testImplementation(testFixtures(projects.modelReflect))
    testImplementation(testFixtures(projects.time))

    integTestImplementation(testFixtures(projects.testingBase))
    integTestImplementation(testFixtures(projects.languageGroovy))

    testRuntimeOnly(projects.distributionsCore) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(projects.distributionsJvm)
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.Test)
    ignoreDeprecations() // uses deprecated software model types
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets = true
tasks.isolatedProjectsIntegTest {
    enabled = false
}
