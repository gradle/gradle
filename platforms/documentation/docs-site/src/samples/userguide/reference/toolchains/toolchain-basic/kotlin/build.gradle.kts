plugins {
    java
}

// tag::toolchain[]
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}
// end::toolchain[]
