rootProject.name = "properties"

// tag::gradle-properties[]
// Using the API, provides a lazy Provider<String>
println(providers.gradleProperty("gradlePropertiesProp").get())

// Using Kotlin delegated properties on `settings`
val gradlePropertiesProp: String by settings
println(gradlePropertiesProp)
// end::gradle-properties[]

// tag::properties-with-dots[]
// In Kotlin scripts, using the API is the only way
println(providers.gradleProperty("gradleProperties.with.dots").get())
// end::properties-with-dots[]
