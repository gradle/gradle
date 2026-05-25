class LicensePlugin implements Plugin<Project> {
    void apply(Project project) {
        // Register a task
        project.tasks.register("greeting") {
            doLast {
                println("Hello from plugin 'com.tutorial.greeting'")
            }
        }
    }
}
