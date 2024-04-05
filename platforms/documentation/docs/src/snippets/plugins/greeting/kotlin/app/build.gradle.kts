// tag::use-plugin[]
plugins {
    application
    id("greetings")
}
// end::use-plugin[]

project.extensions.configure<GreetingPluginExtension>("greeting") {
    message = "Custom message"
}
