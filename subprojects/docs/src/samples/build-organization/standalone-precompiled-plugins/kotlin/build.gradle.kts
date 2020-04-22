plugins {
    `kotlin-dsl`
    `maven-publish`
}

repositories {
    jcenter()
    gradlePluginPortal() // so that external plugins can be resolved in dependencies section
}

dependencies {
    implementation("gradle.plugin.com.github.spotbugs.snom:spotbugs-gradle-plugin:4.0.5")

    testImplementation("junit:junit:4.12")
}

group = "com.example.conventions"
version = "1.0"

publishing {
    repositories {
        maven {
            url = uri("build/my-repo")
        }
    }
}
