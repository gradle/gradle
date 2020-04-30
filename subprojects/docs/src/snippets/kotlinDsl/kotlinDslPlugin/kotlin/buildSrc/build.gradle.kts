// tag::apply[]
// tag::disable-experimental-warning[]
plugins {
    `kotlin-dsl`
}

// end::disable-experimental-warning[]
repositories {
    // The org.jetbrains.kotlin.jvm plugin requires a repository
    // where to download the Kotlin compiler dependencies from.
    jcenter()
}
// end::apply[]

// tag::disable-experimental-warning[]
kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
// end::disable-experimental-warning[]
