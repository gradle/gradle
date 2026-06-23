// tag::do-this[]
plugins {
    `java-library`
}

java {
    toolchain {
        // Choose your project's required version
        languageVersion = JavaLanguageVersion.of(21) // <1>
    }
}
// <2>
// end::do-this[]
