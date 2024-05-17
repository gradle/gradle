plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("projectInfo") {
            id = "com.example.project-info"
            implementationClass = "com.example.ProjectInfoPlugin"
        }
    }
}
