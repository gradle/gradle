plugins {
    id("gradlebuild.distribution.api-java")
}

val integTestRuntimeResources by configurations.creating {
    isCanBeResolved = false
    isCanBeConsumed = false
}
val integTestRuntimeResourcesClasspath by configurations.creating {
    extendsFrom(integTestRuntimeResources)
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        // play test apps MUST be found as exploded directory
        attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements::class.java, LibraryElements.RESOURCES))
    }
    isTransitive = false
}

dependencies {
    implementation(project(":base-services"))
    implementation(project(":logging"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":ide"))
    implementation(project(":language-scala"))
    implementation(project(":platform-base"))
    implementation(project(":platform-jvm"))
    implementation(project(":platform-play"))

    implementation(libs.groovy)
    implementation(libs.guava)

    integTestImplementation(testFixtures(project(":platform-play")))
    integTestImplementation(testFixtures(project(":ide")))

    integTestRuntimeResources(testFixtures(project(":platform-play")))

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}

strictCompile {
    ignoreDeprecations() // Play support in Gradle core has been deprecated
}

tasks.withType<gradlebuild.integrationtests.tasks.IntegrationTest>().configureEach {
    dependsOn(":platform-play:integTestPrepare")
    // this is a workaround for which we need a better fix:
    // it sets the platform play test fixtures resources directory in front
    // of the classpath, so that we can find them when executing tests in
    // an exploded format, rather than finding them in the test fixtures jar
    classpath = integTestRuntimeResourcesClasspath + classpath
}
