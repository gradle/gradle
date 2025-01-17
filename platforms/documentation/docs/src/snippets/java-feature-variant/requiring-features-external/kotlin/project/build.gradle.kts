plugins {
    `java-library`
}

repositories {
    maven {
        url = uri("../repo")
    }
    mavenCentral()
}

// tag::consumer[]
dependencies {
    // This project requires the main producer component
    implementation("org.gradle.demo:producer:1.0")

    // But we also want to use its MongoDB support
    runtimeOnly("org.gradle.demo:producer:1.0") {
        capabilities {
            requireCapability("org.gradle.demo:producer-mongodb-support")
        }
    }
}
// end::consumer[]
