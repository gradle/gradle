plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins {
        create("projectInfo") {
            id = "org.gradle.samples.project-info"
            implementationClass = "org.gradle.samples.ProjectInfoPlugin"
        }
    }
}
