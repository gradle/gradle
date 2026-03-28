plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
}

gradlePlugin {
    plugins {
        create("appInfoPlugin") {
            id = "app-info-plugin"
            implementationClass = "AppInfoPlugin"
        }
    }
}
