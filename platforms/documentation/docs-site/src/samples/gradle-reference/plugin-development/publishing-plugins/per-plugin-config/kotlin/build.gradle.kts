gradlePlugin { // <1>
    // ... // <2>

    plugins { // <3>
        register("greetingsPlugin") { // <4>
            id = "<your plugin identifier>" // <5>
            displayName = "<short displayable name for plugin>" // <6>
            description = "<human-readable description of what your plugin is about>" // <7>
            tags = listOf("tags", "for", "your", "plugins") // <8>
            implementationClass = "<your plugin class>"
        }
    }
}
