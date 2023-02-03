plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("dependencyReports") {
            id = "com.example.dependency-reports"
            implementationClass = "com.example.DependencyReportsPlugin"
        }
    }
}
