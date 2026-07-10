plugins {
    id("java")
}

repositories {
    // tag::metadata-sources[]
    mavenCentral {
        metadataSources {
            mavenPom()
            artifact()
        }
    }
    // end::metadata-sources[]
}
