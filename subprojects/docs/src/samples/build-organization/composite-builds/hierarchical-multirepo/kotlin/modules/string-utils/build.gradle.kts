plugins {
    `java-library`
    `maven-publish`
}

group = "org.sample"
version = "1.0"

dependencies {
    implementation("org.apache.commons:commons-lang3:3.12.0")
}

repositories {
    mavenCentral()
}

publishing {
    repositories {
        maven {
            setUrl(file("../../local-repo"))
        }
    }
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
