plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Tooling API model builders for native builds"

errorprone {
    disabledChecks.addAll(
        "MixedMutabilityReturnType", // 1 occurrences
    )
}

dependencies {
    api(projects.serviceProvider)
    api(project(":core-api"))
    api(project(":core"))
    api(project(":ide")) {
        because("To pick up various builders (which should live somewhere else)")
        api(project(":tooling-api"))
    }

    implementation(projects.baseServices)
    implementation(project(":file-collections"))
    implementation(project(":language-native"))
    implementation(project(":platform-native"))
    implementation(project(":testing-native"))

    implementation(libs.guava)

    testImplementation(testFixtures(project(":platform-native")))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-native"))
}
