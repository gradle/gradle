gradlePlugin {
    website = "https://github.com/ysb33r/gradleTest"
    vcsUrl = "https://github.com/ysb33r/gradleTest.git"
    plugins {
        register("gradletestPlugin") {
            id = "org.ysb33r.gradletest"
            displayName = "Plugin for compatibility testing of Gradle plugins"
            description = "A plugin that helps you test your plugin against a variety of Gradle versions"
            tags = listOf("testing", "integrationTesting", "compatibility")
            implementationClass = "org.ysb33r.gradle.gradletest.GradleTestPlugin"
        }
    }
}
