buildscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.github.ben-manes:gradle-versions-plugin:0.42.0")
    }
}

apply(plugin = "java")
apply(plugin = "jacoco")
apply(plugin = "com.github.ben-manes.versions")
