plugins {
    id("java-library")
}

repositories {
    mavenCentral()
}

// tag::disable-verification[]
configurations {
    create("myConfiguration") {
        resolutionStrategy {
            disableDependencyVerification()
        }
    }
}
// end::disable-verification[]
