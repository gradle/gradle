plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Build operations are our way to inspect the process of executing a build"

gradlebuildJava.usedInWorkers()

dependencies {
    api(libs.jsr305)
    api(projects.stdlibJavaExtensions)
    api(projects.time)

    implementation(libs.slf4jApi)

    testFixturesImplementation(libs.guava)

    testImplementation(testFixtures(projects.time))
}

tasks.isolatedProjectsIntegTest {
    enabled = false
}
