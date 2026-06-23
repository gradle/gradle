plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral() // Use Maven Central for dependencies
}

dependencies {
    implementation("org.junit.jupiter:junit-jupiter:5.10.0")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    implementation("org.junit.platform:junit-platform-launcher:1.10.0")
}

gradlePlugin {
    plugins {
        register("customTest") {
            id = "com.example.custom-test"
            implementationClass = "com.example.CustomTestPlugin"
        }
    }
}
