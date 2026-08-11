plugins {
    java
}

val toolingApiVersion = gradle.gradleVersion

repositories {
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
}

dependencies {
    implementation("org.gradle:gradle-tooling-api:$toolingApiVersion")
    // The tooling API needs an SLF4J implementation available at runtime.
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}
