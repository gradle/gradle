plugins {
    `java-library`
    `maven-publish`
}
// ...

publishing {
    publications {
        create("myLibrary", MavenPublication::class.java) {
            from(components["java"])
        }
    }
}
