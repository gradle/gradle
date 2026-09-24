val versionProvider: Provider<String> = project.provider { "1.0.0" }

println(versionProvider.get()) // Access the value

// Chaining transformations
// tag::transform[]
val majorVersion: Provider<String> = versionProvider.map { it.split(".")[0] }
// end::transform[]
println(majorVersion.get()) // Prints: "1"
