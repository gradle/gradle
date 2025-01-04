plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("org.junit.jupiter:junit-jupiter:5.11.1") // Enforcing specific version
        api("com.google.guava:guava:[33.1.0-jre,)") // Enforcing version range
    }
}