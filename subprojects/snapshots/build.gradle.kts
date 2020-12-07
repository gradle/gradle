plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to take immutable, comparable snapshots of files and other things"

dependencies {
    api(project(":files"))
    api(project(":hashing"))

    implementation(project(":base-annotations"))

    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(project(":process-services"))
    testImplementation(project(":resources"))
    testImplementation(project(":native"))
    testImplementation(project(":persistent-cache"))
    testImplementation(libs.ant)
    testImplementation(libs.commonsIo)
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":core-api")))
    testImplementation(testFixtures(project(":base-services")))
    testImplementation(testFixtures(project(":file-collections")))
    testImplementation(testFixtures(project(":messaging")))

    testFixturesImplementation(project(":base-services"))
    testFixturesImplementation(project(":core-api"))
    testFixturesImplementation(project(":file-collections"))
    testFixturesImplementation(libs.commonsIo)

    integTestDistributionRuntimeOnly(project(":distributions-core"))
}

// This is a workaround for the validate plugins task trying to inspect classes which have changed but are NOT tasks.
// For the current project, we exclude all internal packages, since there are no tasks in there.
tasks.withType<ValidatePlugins>().configureEach {
    classes.setFrom(sourceSets.main.get().output.classesDirs.asFileTree.matching { exclude("**/internal/**") })
}
