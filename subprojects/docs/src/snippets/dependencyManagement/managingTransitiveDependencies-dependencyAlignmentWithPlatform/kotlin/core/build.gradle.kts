plugins {
    `java-library`
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

publishing {
    publications {
        create("maven", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}
