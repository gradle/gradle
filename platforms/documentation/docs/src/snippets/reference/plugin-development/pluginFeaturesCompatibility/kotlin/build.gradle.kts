// tag::compatibility[]
// Kotlin DSL requires importing the extension
import org.gradle.plugin.compatibility.compatibility

plugins {
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "io.github.johndoe"
version = "1.0"

gradlePlugin {
    website = "https://github.com/johndoe/greetings"
    vcsUrl = "https://github.com/johndoe/greetings.git"
    plugins {
        create("greetingsPlugin") {
            id = "io.github.johndoe.greeting"
            implementationClass = "example.GreetingPlugin"
            displayName = "Gradle Greeting plugin"
            description = "Gradle plugin to say hello!"
            tags = listOf("hello", "greeting")

            compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = true
                }
            }
        }
    }
}
// end::compatibility[]
