// tag::apply[]
plugins {
    `java-gradle-plugin`
}
// end::apply[]

group = "org.example"

// tag::config[]
tasks.validatePlugins {
    failOnWarning = true
    enableStricterValidation = true
}
// end::config[]

gradlePlugin {
    plugins {
        create("hello") {
            id = "org.example.hello"
            implementationClass = "org.example.HelloPlugin"
        }
    }
}
