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
    implementation("log4j:log4j:1.2.15@jar")
}
