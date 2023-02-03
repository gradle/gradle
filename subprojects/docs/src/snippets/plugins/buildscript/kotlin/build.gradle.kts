// tag::buildscript_block[]
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath(id("com.jfrog.bintray", "1.8.5")) // <1>
        classpath(plugin("org.jetbrains.kotlin.js", "1.8.0")) // <2>
        classpath(libs.plugins.jmh) // <3>
}

apply(plugin = "com.jfrog.bintray") // <1>
// end::buildscript_block[]
