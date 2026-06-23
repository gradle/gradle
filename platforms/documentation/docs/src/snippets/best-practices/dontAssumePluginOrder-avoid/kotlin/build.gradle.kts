subprojects {
    pluginManager.apply("java")
}
// tag::avoid-this[]
subprojects {
    // Apply the Java plugin to every subproject
    afterEvaluate {
        // This runs after the app subproject’s build script is evaluated and results in an error
        pluginManager.apply("java")
    }
}
// end::avoid-this[]
