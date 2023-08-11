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
    implementation(project(":functional"))
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":file-temp"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":reporting"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm-infrastructure"))
    implementation(project(":toolchains-jvm"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.asm)
    implementation(libs.inject)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

    integTestImplementation(project(":plugins"))
    integTestImplementation(testFixtures(project(":testing-base")))
    integTestImplementation(testFixtures(project(":language-groovy")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.Test)
    ignoreDeprecations() // uses deprecated software model types
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets = true
