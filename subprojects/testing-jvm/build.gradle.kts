plugins {
    id("gradlebuild.distribution.api-java")
}

gradlebuildJava.usedInWorkers()

description = """JVM-specific testing functionality, including support for bootstrapping and configuring test workers, detecting
tests written with various JVM testing frameworks, and executing those tests. This project "extends" the testing-base
project by sub-typing many of its abstractions with JVM-specific abstractions or implementations.

Few projects should need to depend on this module directly. Most external interactions with this module are through the
various implementations of WorkerTestClassProcessorFactory.
"""

dependencies {
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":logging"))
    implementation(project(":file-temp"))
    implementation(project(":core-api"))
    implementation(project(":core"))
    implementation(project(":testing-base"))

    implementation(libs.slf4jApi)
    implementation(libs.groovy) {
        because("Needed for Closure")
    }
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
    testImplementation(project(":reporting"))
    testImplementation(project(":platform-jvm"))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":testing-base")))
    testImplementation(testFixtures(project(":diagnostics")))
    testImplementation(testFixtures(project(":messaging")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":platform-native")))
    testImplementation(testFixtures(project(":language-groovy")))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
    integTestDistributionRuntimeOnly(project(":distributions-jvm"))

    testFixturesImplementation(project(":testing-base"))
    testFixturesImplementation(libs.testng)
    testFixturesImplementation(libs.bsh)
}

packageCycles {
    excludePatterns.add("org/gradle/api/internal/tasks/testing/**")
}

integTest.usesJavadocCodeSnippets.set(true)
