// 3. projectsLoaded: we know the full project graph, but nothing configured yet
// to be called when the projects for the build have been created from the settings.
gradle.projectsLoaded {
    println("[projectsLoaded] Projects discovered: " + rootProject.allprojects.joinToString { it.name })

    // Example: Add a custom property (using the extra properties extension)
    allprojects {
        println("[projectsLoaded] Setting extra property on ${name}")
        extensions.extraProperties["isInitScriptConfigured"] = true
    }
}
