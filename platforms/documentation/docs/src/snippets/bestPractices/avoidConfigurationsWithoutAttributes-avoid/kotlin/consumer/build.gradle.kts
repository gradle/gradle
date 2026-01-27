import java.io.File

// tag::avoid-this[]
val customElementsDependencies = configurations.dependencyScope("customElementsDependencies")

dependencies {
    customElementsDependencies(project(path = ":producer", configuration = "customElements"))
}

val customElements = configurations.resolvable("customElements") { // <2>
    extendsFrom(customElementsDependencies.get())
}

tasks.register("resolveCustom") {
    inputs.files(customElements.get())
    doLast {
        inputs.files.forEach { file: File ->
            logger.lifecycle("Resolved: ${file.name}")
        }
    }
}
// end::avoid-this[]
