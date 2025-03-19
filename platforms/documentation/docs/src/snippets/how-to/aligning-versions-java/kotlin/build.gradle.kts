plugins {
    id("application")
}

dependencies {
    implementation(platform(project(":platform")))

    implementation(project(":core"))
    implementation(project(":lib"))
}
