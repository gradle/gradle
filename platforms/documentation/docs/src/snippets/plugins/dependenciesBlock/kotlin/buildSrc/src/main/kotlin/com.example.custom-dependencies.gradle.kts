import com.example.ExampleExtension

val example = extensions.create<ExampleExtension>("example")

// tag::wire-dependencies[]
configurations {
    dependencyScope("exampleImplementation") {
        fromDependencyCollector(example.dependencies.implementation)
    }
}
// end::wire-dependencies[]
