// tag::use-plugin[]
plugins {
    application
    id("greetings")
}
// end::use-plugin[]

greeting {
    message = "Hello from Gradle"
}
