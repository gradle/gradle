subprojects/docs/src/snippets/files/archivesChangedBaseName/groovy/build.gradle// tag::use-task[]
buildscript {
    repositories {
        maven {
// end::use-task[]
            val repoLocation = "../task/build/repo"
// tag::use-task[]
            url = uri(repoLocation)
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
