plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

gradlePlugin {
    (plugins) {
        register("greet-plugin") {
            id = "greet"
            implementationClass = "GreetPlugin"
        }
    }
}
