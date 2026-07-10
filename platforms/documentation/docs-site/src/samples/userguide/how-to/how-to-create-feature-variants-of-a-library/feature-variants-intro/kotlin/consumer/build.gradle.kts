// tag::consumer[]
plugins {
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":library")) {
        capabilities {
            requireCapability("org.example:library-json-support")
        }
    }
}
// end::consumer[]
