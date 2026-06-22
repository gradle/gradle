// tag::do-this[]
plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21) // <1>
    }
}
// <2>
// end::do-this[]
