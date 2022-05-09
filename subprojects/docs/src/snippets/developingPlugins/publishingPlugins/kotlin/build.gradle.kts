// tag::plugins_block[]
plugins {
    id("com.gradle.plugin-publish") version "1.0.0"
}
// end::plugins_block[]

// tag::global_config[]
group = "io.github.johndoe" // <1>
version = "1.0" // <2>

gradlePlugin { // <3>
    website = "<substitute your project website>" // <4>
    vcsUrl = "<uri to project source repository>" // <5>

    // ... // <6>
}
// end::global_config[]

// tag::per_plugin_config[]
gradlePlugin { // <1>
    // ... // <2>

    plugins { // <3>
        create("greetingsPlugin") { // <4>
            id = "<your plugin identifier>" // <5>
            displayName = "<short displayable name for plugin>" // <6>
            description = "<human-readable description of what your plugin is about>" // <7>
            tags = listOf("tags", "for", "your", "plugins") // <8>
            implementationClass = "<your plugin class>"
        }
    }
}
// end::per_plugin_config[]

// tag::local_repository[]
publishing {
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri("../local-plugin-repository")
        }
    }
}
// end::local_repository[]
