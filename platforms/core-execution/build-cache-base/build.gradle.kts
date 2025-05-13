plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Common shared build cache classes"

gradleModule {
    targetRuntimes {
        usedInClient = true
        usedInDaemon = true
        usedInWorkers = true
    }
}

dependencies {
    implementation(libs.jspecify)

    api(projects.files)
    api(projects.hashing)

    testImplementation(testFixtures(projects.hashing))
}
