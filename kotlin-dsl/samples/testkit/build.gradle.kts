plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("greet") {
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
