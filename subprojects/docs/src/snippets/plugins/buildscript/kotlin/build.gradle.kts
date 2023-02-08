// tag::buildscript_block[]
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath(plugin("org.jetbrains.kotlin.js", "1.8.0")) // <1>
        classpath(libs.plugins.jmh) // <2>
        classpath("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.8.5") // <3>
}

apply(plugin = "com.jfrog.bintray") // <3>
// end::buildscript_block[]
