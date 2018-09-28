plugins {
    java
    signing
}

group = "gradle"
version = "1.0"

// tag::configure-signatory[]
signing {
    useGpgCmd()
    sign(configurations["archives"])
}
// end::configure-signatory[]
