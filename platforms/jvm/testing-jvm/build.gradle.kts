plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.instrumented-project")
}

description = """JVM-specific testing functionality, including the Test type and support for configuring options for and detecting
tests written in various JVM testing frameworks. This project "extends" the testing-base project by sub-typing many
of its abstractions with JVM-specific abstractions or implementations.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build, and is a necessary
dependency for any projects working directly with Test tasks.
"""

errorprone {
    disabledChecks.addAll(
        "EmptyBlockTag", // 1 occurrences
    )
}

dependencies {
    api(projects.javaLanguageExtensions)
    api(projects.time)
    api(project(":base-services"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":core-api"))
    api(project(":logging"))
    api(project(":messaging"))
    api(project(":process-services"))
    api(project(":reporting"))
    api(project(":testing-base"))
    api(project(":testing-base-infrastructure"))
    api(project(":toolchains-jvm"))
    api(project(":toolchains-jvm-shared"))
    api(project(":build-process-services"))

    api(libs.asm)
    api(libs.groovy)
    api(libs.groovyXml)
    api(libs.inject)
    api(libs.jsr305)

    implementation(projects.concurrent)
    implementation(project(":file-temp"))
    implementation(project(":functional"))
    implementation(project(":logging-api"))
    implementation(project(":model-core"))
    implementation(project(":platform-base"))
    implementation(project(":testing-jvm-infrastructure"))

    implementation(libs.commonsIo)
    implementation(libs.commonsLang)
    implementation(libs.guava)
    implementation(libs.junit)
    implementation(libs.slf4jApi)

    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":model-core")))

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
