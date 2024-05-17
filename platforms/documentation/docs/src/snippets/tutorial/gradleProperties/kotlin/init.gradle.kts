// tag::gradle-properties[]
settingsEvaluated {
    // Using the API, provides a lazy Provider<String>
    println(providers.gradleProperty("gradlePropertiesProp").get())

    // Using Kotlin delegated properties on `settings`
    val gradlePropertiesProp: String by this
    println(gradlePropertiesProp)
}
// end::gradle-properties[]
