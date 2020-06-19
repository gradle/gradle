plugins {
    gradlebuild.distribution.`implementation-java`
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

    implementation(library("groovy")) // for 'Closure'
    implementation(library("guava"))
    implementation(library("commons_io"))

    testImplementation(project(":fileCollections"))
}

strictCompile {
    ignoreDeprecations()
}
