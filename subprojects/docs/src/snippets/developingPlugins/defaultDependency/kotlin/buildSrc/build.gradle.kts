plugins {
    id("java-gradle-plugin")
}

gradlePlugin {
    plugins {
        create("dataProcessingPlugin") {
            id = "org.myorg.data-processing"
            implementationClass = "org.myorg.DataProcessingPlugin"
        }
    }
}
