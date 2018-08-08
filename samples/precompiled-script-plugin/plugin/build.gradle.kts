plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
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
