// tag::gradle-properties[]
settingsEvaluated {
    // Using the API, provides a lazy Provider<String>
    println(providers.gradleProperty("gradlePropertiesProp").get())

    // Using the settings API
    val gradlePropertiesProp = providers.gradleProperty("gradlePropertiesProp").get()
    println(gradlePropertiesProp)
}
// end::gradle-properties[]
