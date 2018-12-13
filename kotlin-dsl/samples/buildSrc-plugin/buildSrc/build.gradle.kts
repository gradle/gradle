plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("greet-plugin") {
            id = "greet"
            implementationClass = "GreetPlugin"
        }
    }
}

repositories {
    jcenter()
}
