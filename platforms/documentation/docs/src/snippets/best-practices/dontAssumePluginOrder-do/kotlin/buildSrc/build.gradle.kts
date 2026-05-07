plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "myplugin"
            implementationClass = "MyPlugin"
        }
    }
}
