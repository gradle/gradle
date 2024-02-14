plugins {
    id("java-library")
    id("signing")
}

group = "gradle"
version = "1.0"

// tag::configure-signatory[]
signing {
    useGpgCmd()
    sign(configurations.runtimeElements.get())
}
// end::configure-signatory[]
