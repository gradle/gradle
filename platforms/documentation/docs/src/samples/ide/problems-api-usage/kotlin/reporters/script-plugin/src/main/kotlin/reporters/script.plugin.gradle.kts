package reporters
import org.gradle.api.internal.GradleInternal
import org.gradle.kotlin.dsl.registering

val gradleInternal = gradle as GradleInternal
val problems = gradleInternal.services.get(Problems::class.java)

problems.forNamespace("buildscript").reporting {
    id("Deprecated script plugin")
        .contextualMessage("Deprecated script plugin 'demo-script-plugin'")
        .severity(Severity.WARNING)
        .solution("Please use 'standard-plugin-2' instead of this plugin")
}

tasks {
    val warningTask by registering {
        doLast {
            problems.forNamespace("buildscript").reporting {
                id("Deprecated task"))
                    .contextualMessage("Task 'warningTask' is deprecated")
                    .severity(Severity.WARNING)
                    .solution("Please use 'warningTask2' instead of this task")
            }
        }
    }

    val failingTask by registering {
        doLast {
            problems.forNamespace("buildscript").throwing {
                id("Task should not be called")
                    .contextualMessage("Task 'failingTask' should not be called")
                    .severity(Severity.ERROR)
                    .withException(RuntimeException("The 'failingTask' should not be called"))
                    .solution("Please use 'successfulTask' instead of this task")
            }
        }
    }
}
