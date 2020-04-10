// This script is automatically exposed to downstream consumers
// as the `com.example.my-plugin` org.gradle.api.Project plugin

tasks {
    register("greet") {
        group = "sample"
        doLast {
            println("Hello, World!")
        }
    }
}
