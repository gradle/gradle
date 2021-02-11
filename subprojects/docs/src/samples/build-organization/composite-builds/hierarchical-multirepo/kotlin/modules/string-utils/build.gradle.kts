plugins {
    `java-library`
    `maven-publish`
}

group = "org.sample"
version = "1.0"

dependencies {
    implementation("org.apache.commons:commons-lang3:3.4")
}

repositories {
    maven {
        name = "localrepo"
        url = uri(file("../../../local-repo"))
    }
    mavenCentral()
}


publishing {
    repositories {
        maven {
            setUrl(file("../../../local-repo"))
        }
    }
    publications {
        create<MavenPublication>("maven") { from(components["java"]) }
    }
}
