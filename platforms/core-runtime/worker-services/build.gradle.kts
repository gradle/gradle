plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "A set of services used to setup a build process from a Gradle distribution."

errorprone {
    disabledChecks.addAll(
        "StringSplitter",
    )
}

dependencies {
    api(project(":java-language-extensions"))
    api(project(":base-services"))
    api(libs.jsr305)

    implementation(libs.guava)

    testImplementation(libs.asm)
    testImplementation(libs.asmTree)

    testRuntimeOnly(project(":resources"))
}
