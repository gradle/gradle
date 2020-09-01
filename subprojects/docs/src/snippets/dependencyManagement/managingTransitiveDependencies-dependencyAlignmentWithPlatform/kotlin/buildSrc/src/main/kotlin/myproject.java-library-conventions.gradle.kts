plugins {
    id("java-library")
    id("myproject.publishing-conventions")
}

publishing {
    publications {
        create("maven", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}
