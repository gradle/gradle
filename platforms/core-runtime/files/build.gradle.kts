plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Base tools to work with files"

gradlebuildJava.usedInWorkers()

dependencies {
    api(projects.stdlibJavaExtensions)

    api(libs.jsr305)

    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(projects.native)
    testImplementation(projects.baseServices) {
        because("TextUtil is needed")
    }
    testImplementation(testFixtures(projects.native))
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
