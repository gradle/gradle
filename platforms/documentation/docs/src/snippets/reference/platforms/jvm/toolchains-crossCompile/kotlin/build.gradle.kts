plugins {
    id("java-library")
}

// tag::cross-compile[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}
// end::cross-compile[]
