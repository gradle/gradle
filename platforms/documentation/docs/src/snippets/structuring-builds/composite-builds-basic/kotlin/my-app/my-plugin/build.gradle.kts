plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.example"
version = "1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("helloPlugin") {
            id = "com.example.hello"
            implementationClass = "com.example.plugin.HelloPlugin"
            displayName = "Hello Plugin"
            description = "A tiny example plugin from an included build"
        }
    }
}
