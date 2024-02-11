// tag::plugins[]
plugins {
    id("myproject.java-conventions")
    `kotlin-dsl`
}

repositories {
    // for kotlin-dsl plugin
    gradlePluginPortal()
}
// end::plugins[]
