plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Services and types used to setup a build process from a Gradle distribution."

dependencies {
    api(project(":java-language-extensions"))
    api(project(":base-services"))
    api(libs.jsr305)

    implementation(libs.guava)

    testImplementation(libs.asm)
    testImplementation(libs.asmTree)

    testRuntimeOnly(project(":resources"))
}
