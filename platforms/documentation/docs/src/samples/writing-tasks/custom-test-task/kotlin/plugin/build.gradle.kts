plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("customTest") {
            id = "com.example.custom-test"
            implementationClass = "com.example.CustomTestPlugin"
        }
    }
}
