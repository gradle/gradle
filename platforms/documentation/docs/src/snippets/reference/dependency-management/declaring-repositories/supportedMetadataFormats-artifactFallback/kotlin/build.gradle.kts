plugins {
    id("java")
}

// tag::artifact-fallback[]
repositories {
    maven {
        setUrl("https://repo.example.com/maven")
        metadataSources {
            mavenPom()
            artifact()
        }
    }
}
// end::artifact-fallback[]
