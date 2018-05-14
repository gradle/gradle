plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

gradlePlugin {
    (plugins) {
        "greet" {
            id = "greet"
            implementationClass = "samples.GreetPlugin"
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.12")
}

repositories {
    gradlePluginPortal()
}
