// tag::use-plugin[]
plugins {
    id("org.example.greeting") version "1.0-SNAPSHOT"
}
// end::use-plugin[]

tasks.register<org.example.GreetingTask>("greeting") {
    greeting = "howdy!"
}
