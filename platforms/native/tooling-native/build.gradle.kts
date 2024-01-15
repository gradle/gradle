plugins {
    id("gradlebuild.distribution.api-java")
}

description = "Tooling API model builders for native builds"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":file-collections"))
    implementation(project(":platform-base"))
    implementation(project(":platform-native"))
    implementation(project(":language-native"))
    implementation(project(":testing-native"))
    implementation(project(":tooling-api"))
    implementation(project(":ide")) {
        because("To pick up various builders (which should live somewhere else)")
    }

    implementation(libs.guava)

    testImplementation(testFixtures(project(":platform-native")))

    crossVersionTestDistributionRuntimeOnly(project(":distributions-native"))
}
