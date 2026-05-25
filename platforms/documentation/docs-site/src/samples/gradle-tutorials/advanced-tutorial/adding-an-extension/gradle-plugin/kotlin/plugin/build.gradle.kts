gradlePlugin {
    // Define the plugin
    plugins {
        create("slack") {
            id = "org.example.slack"
            implementationClass = "org.example.SlackPlugin"
        }
    }
}
