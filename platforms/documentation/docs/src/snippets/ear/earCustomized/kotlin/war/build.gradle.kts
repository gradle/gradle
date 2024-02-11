plugins {
    war
}

repositories {
    mavenCentral()
}

configurations {
    create("war") {
        isCanBeResolved = false
        outgoing {
            artifact(tasks["war"])
        }
    }
}

dependencies {
    implementation(group = "log4j", name = "log4j", version = "1.2.15", ext = "jar")
}
