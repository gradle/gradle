plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("standard-plugin") {
            id = "reporters.standard.plugin"
            implementationClass = "reporters.StandardPlugin"
        }
    }
}
