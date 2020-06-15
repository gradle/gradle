plugins {
    gradlebuild.distribution.`api-java`
}

gradlebuildJava.usedForStartup()

dependencies {
    implementation(project(":baseServices"))
    implementation(project(":core"))
    implementation(project(":coreApi"))
    implementation(project(":logging"))
}
