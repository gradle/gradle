plugins {
    // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`
    // Apply the Kotlin JVM plugin to add support for Kotlin.
    alias(libs.plugins.kotlin.jvm)
}

gradlePlugin {
    // Define the plugin
    val greeting by plugins.creating {
        id = "org.example.greeting"
        implementationClass = "org.example.PluginTutorialPlugin"
    }
}
