plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

// tag::direct-constraints[]
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind")

    constraints {
        implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1") {
            because("tested with this version")
        }
    }
}
// end::direct-constraints[]
