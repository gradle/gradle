plugins {
    id("myproject.java-library-conventions")
}

// tag::dependencies[]
dependencies {
    // Each project has a dependency on the platform
    api(platform(project(":platform")))

    // And any additional dependency required
    implementation(project(":lib"))
    implementation(project(":utils"))
}
// end::dependencies[]

