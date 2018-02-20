gradle.projectsEvaluated {
    if (!tasks.findByName("validateTaskProperties")) {
        task validateTaskProperties(type: org.gradle.plugin.devel.tasks.ValidateTaskProperties) {
            dependsOn(sourceSets.main.output)
            classes = sourceSets.main.output.classesDirs
            classpath = sourceSets.main.runtimeClasspath
            outputFile = project.layout.buildDir.file("reports/task-properties/report.txt")
            failOnWarning = true
        }
    }
    validateTaskProperties {
        failOnWarning = true
    }
}
