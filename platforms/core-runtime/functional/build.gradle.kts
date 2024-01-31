plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to work with functional code, including data structures"

errorprone {
    disabledChecks.addAll(
        "UnnecessaryLambda", // 1 occurrences
    )
}

dependencies {
    api(libs.jsr305)

    implementation(project(":base-annotations"))
}
