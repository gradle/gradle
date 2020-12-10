plugins {
    id("gradlebuild.distribution.implementation-java")
}

dependencies {
    implementation(project(":launcher"))
    implementation(project(":base-services"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":process-services"))
    implementation(project(":core-api"))
    implementation(project(":model-core"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":workers"))
    implementation(project(":composite-builds"))
    implementation(project(":tooling-api"))
    implementation(project(":build-events"))

    implementation(libs.groovy) // for 'Closure'
    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(project(":file-collections"))
    testImplementation(project(":platform-jvm"))
    testImplementation(testFixtures(project(":core")))
}

strictCompile {
    ignoreDeprecations()
}
