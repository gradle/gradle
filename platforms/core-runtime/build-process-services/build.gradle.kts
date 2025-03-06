plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Services and types used to setup a build process from a Gradle distribution."

dependencies {
    api(projects.classloaders)
    api(projects.stdlibJavaExtensions)
    api(libs.jsr305)

    implementation(projects.baseServices)

    implementation(libs.guava)

    testImplementation(libs.asm)
    testImplementation(libs.asmTree)

    testRuntimeOnly(projects.resources)
}
