plugins {
    id("gradlebuild.distribution.api-java")
}

description = "The Gradle build option parser."

gradlebuildJava.usedInWorkers()

errorprone {
    disabledChecks.addAll(
        "StringCaseLocaleUsage", // 2 occurrences
    )
}

dependencies {
    api(libs.jsr305)

    api(project(":cli"))
    api(project(":base-annotations"))
    api(project(":messaging"))

    implementation(project(":base-services"))
}
