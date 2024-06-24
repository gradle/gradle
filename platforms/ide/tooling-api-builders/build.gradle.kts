plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provider-side implementation for running tooling model builders"

errorprone {
    disabledChecks.addAll(
        "InlineMeSuggester", // 1 occurrences
    )
}

dependencies {
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":core-api"))
    implementation(project(":dependency-management"))
    implementation(project(":launcher"))
    implementation(project(":resources"))
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":workers"))
    implementation(project(":testing-base-infrastructure"))
    implementation(libs.guava)
    implementation(libs.commonsIo)

    api(libs.jsr305)
    api(project(":base-services"))
    api(project(":build-events"))
    api(project(":build-operations"))
    api(project(":core"))
    api(project(":daemon-protocol"))
    api(project(":enterprise-operations"))
    api(project(":java-language-extensions"))
    api(project(":problems-api"))
    api(project(":service-provider"))
    api(project(":tooling-api"))

    runtimeOnly(project(":composite-builds"))
    runtimeOnly(libs.groovy) // for 'Closure'

    testCompileOnly(project(":toolchains-jvm")) {
        because("JavaLauncher is required for mocking Test.")
    }
    testImplementation(project(":file-collections"))
    testImplementation(project(":platform-jvm"))
    testImplementation(testFixtures(project(":core")))
}

strictCompile {
    ignoreDeprecations()
}
