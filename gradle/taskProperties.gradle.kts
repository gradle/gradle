import org.gradle.plugin.devel.tasks.ValidateTaskProperties

gradle.projectsEvaluated {
    if (tasks.findByName("validateTaskProperties") == null) {
        val sourceSets = the<JavaPluginConvention>().sourceSets
        val main by sourceSets

        tasks {
            "validateTaskProperties"(ValidateTaskProperties::class) {
                dependsOn(main.output)
                classes = main.output.classesDirs
                classpath = main.runtimeClasspath
                outputFile.set(project.layout.buildDirectory.file("reports/task-properties/report.txt"))
                failOnWarning = true
            }
        }
    }
    tasks.getByName("validateTaskProperties").failOnWarning = true
}
