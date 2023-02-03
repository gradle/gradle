plugins {
    java
}

// tag::toolchain[]
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
// end::toolchain[]
