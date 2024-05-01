plugins {
    `java-library`
    id("com.google.protobuf") version ("0.9.1")
}

group = "org.gradle.external"
version = "0.1"

dependencies {
    api("com.google.protobuf:protobuf-java:3.21.12")
}

repositories {
    mavenCentral()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.12"
    }
}
