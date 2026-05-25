// 2. settingsEvaluated: adjust build layout / repositories / scan config
// when the build settings have been loaded and evaluated.
gradle.settingsEvaluated {
    println("[settingsEvaluated] rootProject = ${rootProject.name}")

    // Example: enforce a company-wide pluginManagement repo
    pluginManagement.repositories.apply {
        println("[settingsEvaluated] Ensuring company plugin repo is configured")
        mavenCentral()
    }
}
