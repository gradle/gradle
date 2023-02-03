plugins {
    id("myproject.java-library-conventions")
}

dependencies {
    // Each project has a dependency on the platform
    api(platform(project(":platform")))
}
