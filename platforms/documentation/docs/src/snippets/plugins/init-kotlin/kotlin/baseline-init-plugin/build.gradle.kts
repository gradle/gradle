plugins {
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        create("baselineInitPlugin") {
            id = "com.example.baseline-init-plugin"
            implementationClass = "com.example.BaselineInitPlugin"
            displayName = "Baseline Init Plugin"
            description = "Simple org-wide conventions applied via init script"
        }
    }
}

kotlin {
    jvmToolchain(11)
}

publishing {
    repositories {
        maven {
            name = "mavenRepoInLocalBuild"
            url = uri(project.layout.buildDirectory.dir("repo"))
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
