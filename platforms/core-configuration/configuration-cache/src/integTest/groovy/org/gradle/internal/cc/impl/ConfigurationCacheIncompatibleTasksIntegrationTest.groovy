/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class ConfigurationCacheIncompatibleTasksIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    ConfigurationCacheFixture fixture = new ConfigurationCacheFixture(this)

    def "reports incompatible task serialization error and discards cache entry when task is scheduled"() {
        given:
        withBrokenSerializableType()
        addIncompatibleTasksWithProblems('new BrokenSerializable()')

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            problem "Build file 'build.gradle': line 16: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
            problem "Task `:declared` of type `Broken`: error writing value of type 'BrokenSerializable'"
            incompatibleTask ":declared", "retains configuration container."
        }

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            problem "Build file 'build.gradle': line 16: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
            problem "Task `:declared` of type `Broken`: error writing value of type 'BrokenSerializable'"
            incompatibleTask ":declared", "retains configuration container."
        }
    }

    private withBrokenSerializableType() {
        buildFile '''
            import java.io.*
            @SuppressWarnings('GrMethodMayBeStatic')
            class BrokenSerializable implements Serializable {
                String toString() { 'BrokenSerializable' }
                private Object writeReplace() { throw new RuntimeException('BOOM!') }
            }
        '''
    }

    def "configuration cache report includes incompatible tasks"() {
        given:
        buildFile '''
            task reportedlyIncompatible {
                notCompatibleWithConfigurationCache("declaring myself as not compatible")
            }
        '''

        when:
        configurationCacheRun("reportedlyIncompatible")

        then:
        result.assertTasksExecuted(":reportedlyIncompatible")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }

        fixture.problems.assertResultHasProblems(result) {
            withIncompatibleTask(":reportedlyIncompatible", "declaring myself as not compatible.")
            totalProblemsCount = 0
            problemsWithStackTraceCount = 0
        }
    }

    def "reports incompatible task serialization and execution problems and discards cache entry when task is scheduled"() {
        addIncompatibleTasksWithProblems()

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        assertStateStoredAndDiscardedForDeclaredTask(9)

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        assertStateStoredAndDiscardedForDeclaredTask(9)
    }

    def "problems from incompatible tasks do not count towards max problems limit in #mode-on-problems mode"() {
        given:
        addIncompatibleTasksWithProblems()

        when:
        configurationCacheRun "declared", "$MAX_PROBLEMS_SYS_PROP=1", "--configuration-cache-problems=$mode"

        then:
        result.assertTasksExecuted(":declared")
        assertStateStoredAndDiscardedForDeclaredTask(9)

        where:
        mode   | _
        "fail" | _
        "warn" | _
    }

    def "in warning mode, problems from unmarked tasks count towards max problems limit even when incompatible task is present"() {
        given:
        addIncompatibleTasksWithProblems()

        when:
        configurationCacheFails "declared", "notDeclared", "$MAX_PROBLEMS_SYS_PROP=1", WARN_PROBLEMS_CLI_OPT

        then:
        fixture.problems.assertFailureHasTooManyProblems(failure) {
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
            withProblem("Task `:notDeclared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withIncompatibleTask(":declared", "retains configuration container.")
            totalProblemsCount = 4
            problemsWithStackTraceCount = 2
        }
    }

    def "serialization problems in tasks that are not marked incompatible are treated as failures when incompatible tasks are also scheduled"() {
        given:
        buildFile """
            def capturedProject = project
            tasks.register("broken") {
                doLast {
                    println("use captured project: " + (capturedProject != null)) // serialization problem
                }
            }
            tasks.register("markedIncompatible") {
                notCompatibleWithConfigurationCache("for some reason")
                doLast {
                    println("use captured project: " + (capturedProject != null)) // suppressed serialization problem
                }
            }
        """

        when:
        configurationCacheFails("markedIncompatible", "broken")

        then:
        result.assertTasksExecuted(":markedIncompatible", ":broken")
        fixture.assertStateStoredAndDiscarded {
            loadsAfterStore = false
            serializationProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            serializationProblem("Task `:markedIncompatible` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
        }

        when:
        configurationCacheFails("markedIncompatible", "broken")

        then:
        result.assertTasksExecuted(":markedIncompatible", ":broken")
        fixture.assertStateStoredAndDiscarded {
            loadsAfterStore = false
            serializationProblem("Task `:broken` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
            serializationProblem("Task `:markedIncompatible` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.")
        }
    }

    def "execution-time problems in tasks that are not marked incompatible are treated as interrupting failures when incompatible tasks are also scheduled"() {
        given:
        buildFile """
            def capturedProject = project
            tasks.register("broken") {
                doLast {
                    println("use task.project: " + (project != null)) // execution-time problem
                }
            }
            tasks.register("markedIncompatible") {
                notCompatibleWithConfigurationCache("for some reason")
                doLast {
                    println("use captured project: " + (capturedProject != null)) // suppressed serialization problem
                }
            }
        """

        when:
        configurationCacheFails("markedIncompatible", "broken")

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'.")
        failureCauseContains("Invocation of 'Task.project' by task ':broken' at execution time is unsupported with the configuration cache.")

        result.assertTasksExecuted(":markedIncompatible", ":broken")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            reportedOutsideBuildFailure = true
            serializationProblem "Task `:markedIncompatible` of type `org.gradle.api.DefaultTask`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache."
            problem "Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
        }
    }

    def "discards cache entry when incompatible task scheduled but no problems generated"() {
        addIncompatibleTaskWithoutProblems()

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }
    }

    def "in warning mode, cache is discarded when incompatible task with problems is scheduled"() {
        addIncompatibleTasksWithProblems()

        when:
        configurationCacheRunLenient("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            problem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
            serializationProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            incompatibleTask(":declared", "retains configuration container.")
        }

        when:
        configurationCacheRunLenient("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            problem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
            serializationProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            incompatibleTask(":declared", "retains configuration container.")
        }
    }

    def "in warning mode, cache is discarded when incompatible task without problems is scheduled"() {
        addIncompatibleTaskWithoutProblems()

        when:
        configurationCacheRunLenient("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            incompatibleTask(":declared", "not really.")
        }

        when:
        configurationCacheRunLenient("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            incompatibleTask(":declared", "not really.")
        }
    }

    def "tasks that access project through #providerChain emit no problems when incompatible task is present"() {
        given:
        addIncompatibleTaskWithoutProblems()
        buildFile """
            tasks.register("reliesOnSerialization") { task ->
                dependsOn "declared"
                def projectProvider = $providerChain
                doLast {
                    println projectProvider.get()
                }
            }
        """

        when:
        configurationCacheRun("reliesOnSerialization")

        then:
        result.assertTasksExecuted(":declared", ":reliesOnSerialization")
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }

        where:
        providerChain                               || _
        "provider { task.project.name }"            || _
        "provider { task.project }.map { it.name }" || _
    }

    @ToBeImplemented
    def "tasks that access project through provider created at execution time emit problems when incompatible task is present"() {
        given:
        addIncompatibleTaskWithoutProblems()
        buildFile """
            tasks.register("bypassesSafeguards") {
                dependsOn "declared"
                def providerFactory = providers
                doLast { task ->
                    def projectProvider = providerFactory.provider { task.project.name }
                    println projectProvider.get()
                }
            }
        """

        when:
        // We allow false negative to avoid tracking all providers created at execution time.
        // A desired assertion is configurationCacheFails("bypassesSafeguards")
        configurationCacheRun("bypassesSafeguards")

        then:
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }
    }

    @ToBeImplemented
    def "tasks that access project through indirect provider created at execution time emit problems when incompatible task is present"() {
        given:
        addIncompatibleTaskWithoutProblems()
        buildFile """
            tasks.register("bypassesSafeguards") {
                dependsOn "declared"
                def valueProvider = providers.provider { "value" }
                doLast { task ->
                    println valueProvider.map { it + task.project.name }.get()
                }
            }
        """

        when:
        // We allow false negative to avoid tracking all providers created at execution time.
        // A desired assertion is configurationCacheFails("bypassesSafeguards")
        configurationCacheRun("bypassesSafeguards")

        then:
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }
    }

    @ToBeImplemented
    def "tasks that access project through mapped changing provider emit problems when incompatible task is present"() {
        given:
        addIncompatibleTaskWithoutProblems()
        buildFile """
            tasks.register("bypassesSafeguards") { task ->
                dependsOn "declared"
                def projectProvider = providers.systemProperty("some.property").map { it + task.project.name }
                doLast {
                    println projectProvider.get()
                }
            }
        """

        when:
        // We allow false negative to avoid checking if the provider has fixed execution time value.
        // A desired assertion is configurationCacheFails("bypassesSafeguards", "-Dsome.property=value")
        configurationCacheRun("bypassesSafeguards", "-Dsome.property=value")

        then:
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }
    }

    @ToBeImplemented
    def "tasks that access project through mapped changing value source provider emit problems when incompatible task is present"() {
        given:
        addIncompatibleTaskWithoutProblems()
        buildFile """
            import org.gradle.api.provider.*

            abstract class ChangingSource implements ValueSource<String, ValueSourceParameters.None> {
                @Override
                 String obtain(){
                    return "some string"
                }
            }

            tasks.register("bypassesSafeguards") { task ->
                dependsOn "declared"
                def projectProvider = providers.of(ChangingSource) {}.map { it + task.project.name }
                doLast {
                    println projectProvider.get()
                }
            }
        """

        when:
        // We allow false negative to avoid checking if the provider has fixed execution time value.
        // A desired assertion is configurationCacheFails("bypassesSafeguards")
        configurationCacheRun("bypassesSafeguards")

        then:
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/30043")
    def "can resolve project dependencies at execution time"() {
        given:
        settingsFile """
            include(":other")
        """

        buildFile "other/build.gradle", '''
            plugins { id("java") }
        '''

        buildFile '''
            plugins { id("java") }

            dependencies { implementation(project(':other')) }

            abstract class TaskWithRuntimeClasspath extends DefaultTask {
                @Classpath FileCollection getTaskClasspath() { project.configurations.runtimeClasspath }

                @TaskAction void doIt() {
                    println("Running incompatible task")
                }
            }

            tasks.register("reportedlyIncompatible", TaskWithRuntimeClasspath) {
                notCompatibleWithConfigurationCache("declaring myself as not compatible")
            }
        '''

        when:
        configurationCacheRun("reportedlyIncompatible")

        then:
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
        }
    }

    private void assertStateStoredAndDiscardedForDeclaredTask(int line) {
        fixture.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            loadsAfterStore = false
            problem "Build file 'build.gradle': line $line: invocation of 'Task.project' at execution time is unsupported with the configuration cache."
            serializationProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
        }
    }


    private addIncompatibleTaskWithoutProblems() {
        buildFile """
            tasks.register('declared') {
                notCompatibleWithConfigurationCache("not really")
                doLast {
                }
            }
        """
    }

    private addIncompatibleTasksWithProblems(String brokenFieldValue = 'project.configurations') {
        buildFile """
            class Broken extends DefaultTask {
                // Serialization time problem
                private final brokenField = $brokenFieldValue

                @TaskAction
                void execute() {
                    // Execution time problem
                    project.configurations
                }
            }

            tasks.register("declared", Broken) {
                notCompatibleWithConfigurationCache("retains configuration container")
            }

            tasks.register("notDeclared", Broken) {
            }

            tasks.register("ok") {
                doLast { }
            }
        """
    }
}
