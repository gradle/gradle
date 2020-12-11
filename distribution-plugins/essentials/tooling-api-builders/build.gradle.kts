plugins {
    id("gradlebuild.distribution.implementation-java")
}

dependencies {
    implementation("org.gradle:launcher")
    implementation("org.gradle:base-services")
    implementation("org.gradle:messaging")
    implementation("org.gradle:native")
    implementation("org.gradle:logging")
    implementation("org.gradle:process-services")
    implementation("org.gradle:core-api")
    implementation("org.gradle:model-core")
    implementation("org.gradle:core")
    implementation("org.gradle:base-services-groovy") // for 'Specs'
    implementation("org.gradle:build-events")
    implementation("org.gradle:tooling-api")
    implementation(project(":testing-base"))
    implementation(project(":testing-jvm"))
    implementation(project(":dependency-management"))
    implementation(project(":reporting"))
    implementation(project(":workers"))
    implementation(project(":composite-builds"))

    implementation(libs.groovy) // for 'Closure'
    implementation(libs.guava)
    implementation(libs.commonsIo)

    testImplementation("org.gradle:file-collections")
    testImplementation(project(":platform-jvm"))
    testImplementation(testFixtures("org.gradle:core"))
}

strictCompile {
    ignoreDeprecations()
}
