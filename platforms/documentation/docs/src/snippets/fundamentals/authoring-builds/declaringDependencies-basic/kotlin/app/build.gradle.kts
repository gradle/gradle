plugins {
    java
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// tag::java-dependency-mgmt[]
dependencies {
    implementation("com.google.guava:guava:30.0-jre")
    runtimeOnly("org.apache.commons:commons-lang3:3.14.0")
}
// end::java-dependency-mgmt[]
