plugins {
    `java-library`
    id("gradlebuild.dependency-modules")
    id("gradlebuild.repositories")
    id("gradlebuild.minify")
}

description = "Internal project testing and collecting information about all the test requirements."

dependencies {
    testImplementation(platform(project(":distributions-dependencies")))
    testImplementation(project(":internal-testing"))
    testImplementation(project(":internal-integ-testing"))

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }

    /**
     * List subprojects, which has their own preconditions.
     * These projects should have their preconditions in the "src/testFixtures" sourceSet
     */
    testRuntimeOnly(testFixtures(project(":plugins")))
    testRuntimeOnly(testFixtures(project(":signing")))
    testRuntimeOnly(testFixtures(project(":test-kit")))

    testImplementation(libs.junit5JupiterApi)
    testImplementation(libs.junit5JupiterParams)
    testRuntimeOnly(libs.junit5JupiterEngine)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
