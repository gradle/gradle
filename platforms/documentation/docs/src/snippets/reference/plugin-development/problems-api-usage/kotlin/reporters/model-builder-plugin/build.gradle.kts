plugins {
    `java-gradle-plugin`
}

group = "reporters"

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("model-builder-plugin") {
            id = "reporters.model.builder"
            implementationClass = "reporters.ModelBuilderPlugin"
        }
    }
}
