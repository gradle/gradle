plugins {
    application
}

repositories {
    mavenCentral()
}

// tag::avoid-this[]
tasks.register("generateDocs") {
    // Build logic to generate documentation
}
// end::avoid-this[]
