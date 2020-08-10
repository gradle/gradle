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
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":base-services-groovy")) // for 'Specs'
    implementation(project(":testingBase"))
    implementation(project(":testingJvm"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":workers"))
    implementation(project(":compositeBuilds"))
    implementation(project(":tooling-api"))
    implementation(project(":build-events"))

    implementation(libs.groovy) // for 'Closure'
    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(project(":fileCollections"))
    testImplementation(project(":platformJvm"))
}

strictCompile {
    ignoreDeprecations()
}
