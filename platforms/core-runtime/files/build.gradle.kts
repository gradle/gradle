plugins {
    id("gradlebuild.distribution.api-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Base tools to work with files"

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "InlineMeInliner", // 1 occurrences
        "ReferenceEquality", // 1 occurrences
        "StringSplitter", // 1 occurrences
        "UnusedMethod", // 1 occurrences
    )
}

dependencies {
    api(project(":functional"))
    api(project(":base-annotations"))

    api(libs.jsr305)

    implementation(libs.guava)
    implementation(libs.slf4jApi)

    testImplementation(project(":native"))
    testImplementation(project(":base-services")) {
        because("TextUtil is needed")
    }
    testImplementation(testFixtures(project(":native")))
}
