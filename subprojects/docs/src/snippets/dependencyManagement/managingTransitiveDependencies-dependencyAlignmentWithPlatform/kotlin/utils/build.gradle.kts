plugins {
    `java-library`
}

dependencies {
    // Each project has a dependency on the platform
    api(platform(project(":platform")))
}

publishing {
    publications {
        create("maven", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}
