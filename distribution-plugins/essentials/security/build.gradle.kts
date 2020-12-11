plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Shared classes for projects requiring GPG support"

dependencies {
    api("org.gradle:core-api")
    api("org.gradle:resources")
    implementation("org.gradle:base-services")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:resources-http")
    implementation(libs.guava)

    api(libs.bouncycastlePgp)

    implementation(libs.groovy) {
        because("Project.exec() depends on Groovy")
    }

    testImplementation(testFixtures("org.gradle:core"))

    testFixturesImplementation("org.gradle:base-services")
    testFixturesImplementation(libs.slf4jApi)
    testFixturesImplementation(libs.jetty)
    testFixturesImplementation(libs.jettyWebApp)
    testFixturesImplementation(testFixtures("org.gradle:core"))
    testFixturesImplementation("org.gradle:internal-integ-testing")

    testRuntimeOnly(project(":distributions-core")) {
        because("Tests instantiate DefaultClassLoaderRegistry which requires a 'gradle-plugins.properties' through DefaultPluginModuleRegistry")
    }
}

classycle {
    excludePatterns.add("org/gradle/plugins/signing/type/pgp/**")
}
