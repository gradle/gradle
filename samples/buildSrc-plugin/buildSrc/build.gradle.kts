plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    (plugins) {
        "my-plugin" {
            id = "greet"
            implementationClass = "my.GreetPlugin"
        }
    }
}