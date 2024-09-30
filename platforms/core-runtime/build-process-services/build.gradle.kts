plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Services and types used to setup a build process from a Gradle distribution."

dependencies {
    api(projects.stdlibJavaExtensions)
    api(projects.baseServices)
    api(libs.jsr305)

    implementation(libs.guava)

    testImplementation(libs.asm)
    testImplementation(libs.asmTree)

    testRuntimeOnly(projects.resources)
}
tasks.isolatedProjectsIntegTest {
    enabled = false
}
