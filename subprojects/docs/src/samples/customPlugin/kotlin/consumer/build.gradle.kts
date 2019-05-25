// tag::use-plugin[]
// tag::use-task[]
buildscript {
    repositories {
        maven {
// end::use-task[]
// end::use-plugin[]
            val producerName = findProperty("producerName") ?: "plugin"
            val repoLocation = "../$producerName/build/repo"
// tag::use-plugin[]
// tag::use-task[]
            url = uri(repoLocation)
        }
    }
    dependencies {
        classpath("org.gradle:customPlugin:1.0-SNAPSHOT")
    }
}
// end::use-task[]
apply(plugin = "org.samples.greeting")
// end::use-plugin[]
// tag::use-task[]

tasks.register<org.gradle.GreetingTask>("greeting") {
    greeting = "howdy!"
}
// end::use-task[]
