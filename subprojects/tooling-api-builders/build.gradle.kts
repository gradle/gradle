plugins {
    id("gradlebuild.distribution.implementation-java")
}

description = "Provider-side implementation for running tooling model builders"

dependencies {
    implementation(project(":base-services"))
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":enterprise-operations"))
    implementation(project(":build-events"))
    implementation(project(":composite-builds"))
    implementation(project(":core"))
    implementation(project(":core-api"))
    implementation(project(":functional"))
    implementation(project(":dependency-management"))
    implementation(project(":launcher"))
    implementation(project(":logging"))
    implementation(project(":messaging"))
    implementation(project(":model-core"))
    implementation(project(":native"))
    implementation(project(":plugins"))
    implementation(project(":process-services"))
    implementation(project(":reporting"))
    implementation(project(":resources"))
    implementation(project(":testing-base"))
    implementation(project(":tooling-api"))
    implementation(project(":workers"))

    implementation(libs.groovy) {
        because("Needed for Closure")
    }
    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(project(":file-collections"))
    testImplementation(project(":platform-jvm"))
    testImplementation(project(":testing-jvm"))
    testImplementation(testFixtures(project(":core")))
}

strictCompile {
    ignoreDeprecations()
}
