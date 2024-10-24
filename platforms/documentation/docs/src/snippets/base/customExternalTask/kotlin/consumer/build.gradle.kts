// tag::use-task[]
buildscript {
    repositories {
        maven {
// end::use-task[]
            val repoLocation = file("../task/build/repo")
// tag::use-task[]
            url = repoLocation
        }
    }
    dependencies {
        classpath("org.gradle:task:1.0-SNAPSHOT")
    }
}

tasks.register<org.gradle.GreetingTask>("greeting") {
    greeting = "howdy!"
}
// end::use-task[]
