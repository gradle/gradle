plugins {
    id("java")
}

repositories {
    mavenCentral()
}

// tag::changing-deps[]
dependencies {
    implementation("com.example:some-library:1.0-SNAPSHOT") // Automatically gets treated as changing
    implementation("com.example:my-library:1.0") {  // Must be explicitly set as changing
        isChanging = true
    }
}
// end::changing-deps[]
