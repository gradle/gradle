plugins {
    id("gradlebuild.distribution.implementation-java")
}

dependencies {
    implementation(project(":launcher"))
    implementation(project(":baseServices"))
    implementation(project(":messaging"))
    implementation(project(":native"))
    implementation(project(":logging"))
    implementation(project(":processServices"))
    implementation(project(":coreApi"))
    implementation(project(":modelCore"))
    implementation(project(":core"))
    implementation(project(":baseServicesGroovy")) // for 'Specs'
    implementation(project(":testingBase"))
    implementation(project(":testingJvm"))
    implementation(project(":dependencyManagement"))
    implementation(project(":reporting"))
    implementation(project(":workers"))
    implementation(project(":compositeBuilds"))
    implementation(project(":toolingApi"))
    implementation(project(":buildEvents"))

    implementation(libs.groovy) // for 'Closure'
    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation(project(":fileCollections"))
    testImplementation(project(":platformJvm"))
}

strictCompile {
    ignoreDeprecations()
}
