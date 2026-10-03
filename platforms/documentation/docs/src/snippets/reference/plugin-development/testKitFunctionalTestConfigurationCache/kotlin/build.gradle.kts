plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    plugins {
        create("myPlugin") {
            id = "org.gradle.sample.my-plugin"
            implementationClass = "org.gradle.sample.MyPlugin"
        }
    }
}
