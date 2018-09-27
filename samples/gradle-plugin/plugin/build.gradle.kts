plugins {
    `kotlin-dsl`
    `maven-publish`
}

group = "my"
version = "1.0"

gradlePlugin {
    plugins {
        register("myPlugin") {
            id = "my-plugin"
            implementationClass = "plugin.MyPlugin"
        }
    }
}

publishing {
    repositories {
        maven(url = "build/repository")
    }
}

repositories {
    jcenter()
}
