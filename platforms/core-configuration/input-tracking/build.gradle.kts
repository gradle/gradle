plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Configuration input discovery code"

errorprone {
    disabledChecks.addAll(
        "HashtableContains",
        "UnsynchronizedOverridesSynchronized", // 29 occurrences
    )
}

dependencies {
    api(libs.jsr305)
    api(libs.guava)

    implementation(project(":base-annotations"))
}
