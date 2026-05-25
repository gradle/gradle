gradlePlugin {
    plugins {
        create("simplePlugin") {
            id = "org.gradle.sample.simple-plugin"
            implementationClass = "org.gradle.sample.SimplePlugin"
        }
    }
}
