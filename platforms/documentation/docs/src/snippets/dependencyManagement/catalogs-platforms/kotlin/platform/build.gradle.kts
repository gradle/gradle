plugins {
    `java-platform`
}

javaPlatform {
    allowDependencies()
}

dependencies {
    constraints {
        api("org.junit.jupiter:junit-jupiter:5.11.1") // Enforcing version range
        api("com.google.guava:guava:[33.1.0-jre,)") // Enforcing specific version
    }
}