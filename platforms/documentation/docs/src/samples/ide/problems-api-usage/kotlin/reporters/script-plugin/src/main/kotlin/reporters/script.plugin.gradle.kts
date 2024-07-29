package reporters
import org.gradle.kotlin.dsl.registering

interface Injected {
    @get:Inject val problems: Problems
}

val problems = project.objects.newInstance<Injected>().problems

problems.forNamespace("buildscript").reporting {
    id("adhoc-script-deprecation", "Deprecated script plugin")
        .contextualLabel("Deprecated script plugin 'demo-script-plugin'")
        .severity(Severity.WARNING)
        .solution("Please use 'standard-plugin-2' instead of this plugin")
}

tasks {
    val warningTask by registering {
        doLast {
            problems.forNamespace("buildscript").reporting {
                id("adhoc-task-deprecation", "Deprecated task")
                    .contextualLabel("Task 'warningTask' is deprecated")
                    .severity(Severity.WARNING)
                    .solution("Please use 'warningTask2' instead of this task")
            }
        }
    }

    val failingTask by registering {
        doLast {
            problems.forNamespace("buildscript").throwing {
                id("broken-task", "Task should not be called")
                    .contextualLabel("Task 'failingTask' should not be called")
                    .severity(Severity.ERROR)
                    .withException(RuntimeException("The 'failingTask' should not be called"))
                    .solution("Please use 'successfulTask' instead of this task")
            }
        }
    }
}
