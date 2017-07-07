plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

group = "my"
version = "1.0"

gradlePlugin {
    (plugins) {
        "myPlugin" {
            id = "my-plugin"
            implementationClass = "plugin.MyPlugin"
        }
    }
}

publishing {
    repositories {
        maven {
            url = uri("build/repository")
        }
    }
}
