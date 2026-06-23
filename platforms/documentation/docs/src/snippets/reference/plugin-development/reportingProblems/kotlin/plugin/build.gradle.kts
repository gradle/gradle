plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

group = "org.example"
version = "1.0.0"

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("helloProblems") {
            id = "org.example.hello-problems"
            implementationClass = "org.example.HelloProblemsPlugin"
            displayName = "Hello Problems Plugin"
            description = "Adds a greet task and demonstrates Gradle's Problems API."
        }
    }
}
