plugins {
    id("java-library")
}

// tag::toolchain[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
// end::toolchain[]
