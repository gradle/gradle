plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
}

group = "my"

version = "1.0"

publishing {
    repositories {
        maven(url = "build/repository")
    }
}

repositories {
    jcenter()
}
