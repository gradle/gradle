plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """JVM-specific testing functionality, including the Test type and support for configuring options for, detecting
tests written in and running various JVM testing frameworks.  This project "extends" the testing-base project by sub-typing many
of its abstractions with JVM-specific abstractions or implementations.

This project is a implementation dependency of many other testing-related subprojects in the Gradle build, and is a necessary
dependency for any projects working directly with Test tasks.
"""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":file-collections"))
    implementation(project(":file-temp"))
    implementation(project(":jvm-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":diagnostics"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":language-java"))
    implementation(project(":testing-base"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy)
    implementation(libs.groovyXml)
    implementation(libs.guava)
    implementation(libs.commonsLang)
    implementation(libs.commonsIo)
    implementation(libs.asm)
    implementation(libs.junit)
    implementation(libs.testng)
    implementation(libs.inject)
    implementation(libs.bsh)

    testImplementation(project(":base-services-groovy"))
    testImplementation(project(":plugins"))
    testImplementation(libs.guice) {
        because("This is for TestNG")
    }
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":testing-base")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":platform-native")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))
}

strictCompile {
    ignoreRawTypes() // raw types used in public API (org.gradle.api.tasks.testing.Test)
    ignoreDeprecations() // uses deprecated software model types
}

classycle {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

tasks.test {
    exclude("org/gradle/api/internal/tasks/testing/junit/AJunit*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/BJunit*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestSetUp*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ABroken*TestClass*.*")
    exclude("org/gradle/api/internal/tasks/testing/junit/ATestSetUpWithBrokenSetUp*.*")
    exclude("org/gradle/api/internal/tasks/testing/testng/ATestNGFactoryClass*.*")
}

integTest.usesJavadocCodeSnippets.set(true)
