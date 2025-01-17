package reporters
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.kotlin.dsl.registering

interface Injected {
    @get:Inject val problems: Problems
}

val problems = project.objects.newInstance<Injected>().problems
val problemGroup = ProblemGroup.create("root", "Root Group")

problems.getReporter().report(ProblemId.create("adhoc-script-deprecation", "Deprecated script plugin", problemGroup)) {
    contextualLabel("Deprecated script plugin 'demo-script-plugin'")
        .severity(Severity.WARNING)
        .solution("Please use 'standard-plugin-2' instead of this plugin")
}

tasks {
    val warningTask by registering {
        doLast {
            problems.getReporter().report(ProblemId.create("adhoc-task-deprecation", "Deprecated task", problemGroup)) {
                contextualLabel("Task 'warningTask' is deprecated")
                    .severity(Severity.WARNING)
                    .solution("Please use 'warningTask2' instead of this task")
            }
        }
    }

    val failingTask by registering {
        doLast {
            problems.getReporter().throwing(RuntimeException("The 'failingTask' should not be called"), ProblemId.create("broken-task", "Task should not be called", problemGroup)) {
                    contextualLabel("Task 'failingTask' should not be called")
                    .severity(Severity.ERROR)
                    .solution("Please use 'successfulTask' instead of this task")
            }
        }
    }
}
