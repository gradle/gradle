plugins {
    `java-library`
    `maven-publish`
}

version = "1.0"
group = "com.example"

repositories {
    mavenCentral()
}

configurations {
    // Declaring that this library provides the logging capability
    apiElements {
        outgoing {
            capability("com.example:lib-a:1.0")
            capability("com.example:logging:1.0")
        }
    }
    runtimeElements {
        outgoing {
            capability("com.example:lib-a:1.0")
            capability("com.example:logging:1.0")
        }
    }
}

dependencies {
    api("org.slf4j:slf4j-api:1.7.30")
}

publishing {
    publications {
        create<MavenPublication>("library") {
            from(components["java"])
        }
    }
    repositories {
        maven {
            url = uri(layout.buildDirectory.dir("publishing-repository"))
        }
    }
}
