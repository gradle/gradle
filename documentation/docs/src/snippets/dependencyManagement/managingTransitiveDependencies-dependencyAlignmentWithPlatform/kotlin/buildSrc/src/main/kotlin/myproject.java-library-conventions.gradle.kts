plugins {
    id("java-library")
    id("myproject.publishing-conventions")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
