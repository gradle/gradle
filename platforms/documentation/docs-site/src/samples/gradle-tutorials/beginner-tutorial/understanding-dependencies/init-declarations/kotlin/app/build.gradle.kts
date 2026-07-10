repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {  // <2>
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
}
