// tag::buildscript_block[]
buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath(id("com.jfrog.bintray", "1.8.5")) // <1>
        classpath(libs.plugins.jmh) // <2>
    }
}

apply(plugin = "com.jfrog.bintray") // <1>
// end::buildscript_block[]
