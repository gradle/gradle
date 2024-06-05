plugins {
    id("gradlebuild.distribution.api-java")
}

description = "File system watchers for keeping the VFS up-to-date"

dependencies {
    api(project(":snapshots"))
    api(project(":build-operations"))
    api(project(":files"))
    api(projects.javaLanguageExtensions)

    api(libs.jsr305)
    api(libs.nativePlatform)
    api(libs.nativePlatformFileEvents)
    api(libs.slf4jApi)
    api(libs.guava)

    implementation(project(":functional"))

    testImplementation(project(":process-services"))
    testImplementation(project(":resources"))
    testImplementation(project(":persistent-cache"))
    testImplementation(project(":build-option"))
    testImplementation(project(":enterprise-operations"))
    testImplementation(testFixtures(project(":build-operations")))
    testImplementation(testFixtures(project(":core")))
    testImplementation(testFixtures(project(":file-collections")))
    testImplementation(testFixtures(project(":tooling-api")))
    testImplementation(testFixtures(project(":launcher")))
    testImplementation(testFixtures(project(":snapshots")))

    testImplementation(libs.commonsIo)

    integTestDistributionRuntimeOnly(project(":distributions-jvm")) {
        because("Uses application plugin.")
    }
}
