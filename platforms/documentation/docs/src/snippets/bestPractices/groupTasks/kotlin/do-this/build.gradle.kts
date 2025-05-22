plugins {
    application
}

repositories {
    mavenCentral()
}

// tag::do-this[]
tasks.register("generateDocs") {
    group = "documentation"
    description = "Generates project documentation from source files."
    // Build logic to generate documentation
}
// end::do-this[]
