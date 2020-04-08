plugins {
    gradlebuild.distribution.`core-api-java`
    gradlebuild.classycle
}

gradlebuildJava.usedForStartup()

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":core"))
    implementation(project(":coreApi"))
    implementation(project(":logging"))
}
