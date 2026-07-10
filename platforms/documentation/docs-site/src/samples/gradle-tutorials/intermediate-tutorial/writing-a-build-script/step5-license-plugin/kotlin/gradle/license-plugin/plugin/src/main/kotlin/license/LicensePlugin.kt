class LicensePlugin: Plugin<Project> {
    override fun apply(project: Project) {                          // Apply plugin
        project.tasks.register("greeting") { task ->                // Register a task
            task.doLast {
                println("Hello from plugin 'com.tutorial.greeting'")  // Hello world printout
            }
        }
    }
}
