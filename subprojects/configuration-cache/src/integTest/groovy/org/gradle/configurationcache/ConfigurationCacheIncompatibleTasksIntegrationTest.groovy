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

package org.gradle.configurationcache

import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class ConfigurationCacheIncompatibleTasksIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    ConfigurationCacheFixture fixture = new ConfigurationCacheFixture(this)

    def "reports incompatible task serialization error and discards cache entry when task is scheduled"() {
        given:
        withBrokenSerializableType()
        addTasksWithProblems('new BrokenSerializable()')

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            problem "Build file 'build.gradle': line 16: invocation of 'Task.project' at execution time is unsupported."
            problem "Task `:declared` of type `Broken`: error writing value of type 'BrokenSerializable'"
        }

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
            problem "Build file 'build.gradle': line 16: invocation of 'Task.project' at execution time is unsupported."
            problem "Task `:declared` of type `Broken`: error writing value of type 'BrokenSerializable'"
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

    def "reports incompatible task serialization and execution problems and discards cache entry when task is scheduled"() {
        addTasksWithProblems()

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

    def "incompatible task problems are not subtracted from max-problems"() {
        given:
        addTasksWithProblems()

        when:
        configurationCacheRun "declared", "$MAX_PROBLEMS_SYS_PROP=1"

        then:
        result.assertTasksExecuted(":declared")
        assertStateStoredAndDiscardedForDeclaredTask(9)
    }

    def "incompatible task problems are not subtracted from max-problems but problems from tasks that are not marked incompatible are"() {
        given:
        addTasksWithProblems()

        when:
        configurationCacheFails "declared", "notDeclared", "$MAX_PROBLEMS_SYS_PROP=2", WARN_PROBLEMS_CLI_OPT

        then:
        fixture.problems.assertFailureHasTooManyProblems(failure) {
            withProblem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Task `:declared` of type `Broken`: cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            withProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            withProblem("Task `:notDeclared` of type `Broken`: cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            withProblem("Task `:notDeclared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            totalProblemsCount = 6
            problemsWithStackTraceCount = 2
        }
    }

    def "problems in tasks that are not marked incompatible are treated as failures when incompatible tasks are also scheduled"() {
        given:
        addTasksWithProblems()

        when:
        configurationCacheFails("declared", "notDeclared")

        then:
        result.assertTasksExecuted(":declared", ":notDeclared")
        assertStateStoredAndDiscardedForDeclaredAndNotDeclaredTasks()

        when:
        configurationCacheFails("declared", "notDeclared")

        then:
        result.assertTasksExecuted(":declared", ":notDeclared")
        assertStateStoredAndDiscardedForDeclaredAndNotDeclaredTasks()
    }

    def "discards cache entry when incompatible task scheduled but no problems generated"() {
        addTasksWithoutProblems()

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
        }

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredAndDiscarded {
        }
    }

    def "can force storing cache entry by treating problems as warnings"() {
        addTasksWithProblems()

        when:
        configurationCacheRunLenient("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStoredWithProblems {
            problem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            serializationProblem("Task `:declared` of type `Broken`: cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
            serializationProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
        }

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateLoadedWithProblems {
            problem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.")
            serializationProblem("Task `:declared` of type `Broken`: cannot deserialize object of type 'org.gradle.api.artifacts.ConfigurationContainer' as these are not supported with the configuration cache.")
        }
    }

    def "can force storing cache entry by treating problems as warnings when incompatible task is scheduled but has no problems"() {
        addTasksWithoutProblems()

        when:
        configurationCacheRunLenient("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateStored {
        }

        when:
        configurationCacheRun("declared")

        then:
        result.assertTasksExecuted(":declared")
        fixture.assertStateLoaded()
    }

    private void assertStateStoredAndDiscardedForDeclaredTask(int line) {
        fixture.assertStateStoredAndDiscarded {
            problem "Build file 'build.gradle': line $line: invocation of 'Task.project' at execution time is unsupported."
            serializationProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
        }
    }

    private void assertStateStoredAndDiscardedForDeclaredAndNotDeclaredTasks() {
        fixture.assertStateStoredAndDiscarded {
            problem("Build file 'build.gradle': line 9: invocation of 'Task.project' at execution time is unsupported.", 2)
            serializationProblem("Task `:declared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
            serializationProblem("Task `:notDeclared` of type `Broken`: cannot serialize object of type 'org.gradle.api.internal.artifacts.configurations.DefaultConfigurationContainer', a subtype of 'org.gradle.api.artifacts.ConfigurationContainer', as these are not supported with the configuration cache.")
        }
    }

    private addTasksWithoutProblems() {
        buildFile """
            tasks.register('declared') {
                notCompatibleWithConfigurationCache("not really")
                doLast {
                }
            }
        """
    }

    private addTasksWithProblems(String brokenFieldValue = 'project.configurations') {
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
