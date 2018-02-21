var afterEvaluation = false

gradle.projectsEvaluated {
    afterEvaluation = true
}

allprojects {
    configurations.all {
        val configName = this.name
        incoming.beforeResolve {
           if (!afterEvaluation) {
               throw Exception("Configuration $configName of project ${project.name} is being resolved at configuration time.")
           }
        }
    }
}
