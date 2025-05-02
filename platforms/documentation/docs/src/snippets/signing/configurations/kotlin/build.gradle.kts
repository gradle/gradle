// tag::use-plugin[]
plugins {
    // end::use-plugin[]
    java
// tag::use-plugin[]
    signing
}
// end::use-plugin[]


group = "gradle"
version = "1.0"

// Typically set in ~/.gradle/gradle.properties
extra["signing.keyId"] = "24875D73"
extra["signing.password"] = "gradle"
extra["signing.secretKeyRingFile"] = file("secKeyRingFile.gpg").absolutePath

// tag::sign-runtime-elements[]
signing {
    sign(configurations.runtimeElements.get())
}
// end::sign-runtime-elements[]
