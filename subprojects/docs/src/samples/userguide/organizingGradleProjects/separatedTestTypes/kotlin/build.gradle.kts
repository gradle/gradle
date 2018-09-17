plugins {
    java
}
apply(from = "gradle/integration-test.gradle.kts")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("junit:junit:4.12")
}
