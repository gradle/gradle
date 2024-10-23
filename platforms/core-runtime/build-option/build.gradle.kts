plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Gradle build option parser."

gradlebuildJava.usedInWorkers()

dependencies {
    api(libs.jsr305)

    api(projects.cli)
    api(projects.stdlibJavaExtensions)
    api(projects.messaging)

    implementation(projects.baseServices)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
