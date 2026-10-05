package reporters

interface Injected {
    @get:Inject val problems: Problems
}

val problems = project.objects.newInstance<Injected>().problems
val problemGroup = problems.getGroups().getOthers().group("Root Group")

problems.getReporter().report(problemGroup.problemId("Deprecated script plugin")) {
    contextualLabel("Deprecated script plugin 'demo-script-plugin'")
        .solution("Please use 'standard-plugin-2' instead of this plugin")
}

tasks {
    register("warningTask") {
        doLast {
            problems.getReporter().report(problemGroup.problemId("Deprecated task")) {
                contextualLabel("Task 'warningTask' is deprecated")
                    .solution("Please use 'warningTask2' instead of this task")
            }
        }
    }

    register("failingTask") {
        doLast {
            problems.getReporter().throwing(RuntimeException("The 'failingTask' should not be called"), problemGroup.problemId("Task should not be called")) {
                    contextualLabel("Task 'failingTask' should not be called")
                    .solution("Please use 'successfulTask' instead of this task")
            }
        }
    }
}
