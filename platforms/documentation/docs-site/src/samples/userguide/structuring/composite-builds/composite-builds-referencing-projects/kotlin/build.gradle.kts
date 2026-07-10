plugins {
    id("java-library")
}

// tag::referencing-projects[]
dependencies {
    // ❌ This will fail because ":core-schema" is not in this build's hierarchy
    implementation(project(":core-schema"))

    // ✅ Gradle sees this GAV and finds the matching project in the included build
    implementation("com.example.data:core-schema:1.0.0")
}
// end::referencing-projects[]
