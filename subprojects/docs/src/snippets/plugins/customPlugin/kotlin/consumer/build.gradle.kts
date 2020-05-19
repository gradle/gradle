// tag::use-plugin[]
plugins {
    id("org.samples.greeting") version "1.0-SNAPSHOT"
}
// end::use-plugin[]

tasks.register<org.gradle.GreetingTask>("greeting") {
    greeting = "howdy!"
}
