plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    (plugins) {
        "greet-plugin" {
            id = "greet"
            implementationClass = "GreetPlugin"
        }
    }
}
