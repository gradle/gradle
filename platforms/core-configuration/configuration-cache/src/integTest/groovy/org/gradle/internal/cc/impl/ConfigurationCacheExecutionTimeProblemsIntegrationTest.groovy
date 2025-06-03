/*
 * Copyright 2025 the original author or authors.
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

class ConfigurationCacheExecutionTimeProblemsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def configurationCache = new ConfigurationCacheFixture(this)

    def "task fails and configuration is not cached due to #invocation access in annotated task action"() {
        buildFile """
            abstract class MyTask extends DefaultTask {
                @TaskAction
                def action() {
                    $code
                    throw new IllegalStateException("UNREACHABLE")
                }
            }

            tasks.register("broken", MyTask)
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'.")
        failureCauseContains("Invocation of '$invocation' by task ':broken' at execution time is unsupported.")
        outputDoesNotContain("UNREACHABLE")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedInRegularOutputDespiteFailure = true
            problem "Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported."
        }

        where:
        invocation              | code
        'Task.project'          | 'project.name'
        'Task.dependsOn'        | 'dependsOn'
        'Task.taskDependencies' | 'taskDependencies'
    }

    def "task fails and configuration is not cached due to Task.#invocation access in #method #desc action"() {
        buildFile """
            class MyAction implements Action<Task> {
                void execute(Task task) {
                    task.$invocation
                    throw new IllegalStateException("UNREACHABLE")
                }
            }

            tasks.register("broken") {
                $method$code
            }
        """

        when:
        configurationCacheFails "broken"

        then:
        failureDescriptionStartsWith("Execution failed for task ':broken'.")
        failureCauseContains("Invocation of 'Task.$invocation' by task ':broken' at execution time is unsupported.")
        outputDoesNotContain("UNREACHABLE")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            reportedInRegularOutputDespiteFailure = true
            problem "Build file 'build.gradle': line 4: invocation of 'Task.$invocation' at execution time is unsupported."
        }

        where:
        [invocation, method, [desc, code]] << [
            ['project', 'dependsOn', 'taskDependencies'],
            ['doFirst', 'doLast'],
            [
                ['custom', '(new MyAction())'],
                ['lambda', '{ new MyAction().execute(it) }'],
            ]
        ].combinations()
    }

    def "in warning mode, task does not fail and configuration can be reused despite execution-time problem"() {
        buildFile """
            version = "foo-version"

            tasks.register('broken') {
                doLast {
                    println("At execution: version='\${project.version}'")
                }
            }
        """

        when:
        configurationCacheRunLenient "broken"

        then:
        executed(":broken")
        outputContains("At execution: version='unspecified'")
        outputDoesNotContain("foo-version")

        and:
        configurationCache.assertStateStoredWithProblems {
            problem("Build file 'build.gradle': line 6: invocation of 'Task.project' at execution time is unsupported.")
        }

        when: "running again"
        configurationCacheRunLenient "broken"

        then: "observing the same execution result"
        executed(":broken")
        outputContains("At execution: version='unspecified'")
        outputDoesNotContain("foo-version")

        and: "configuration cache is reused"
        configurationCache.assertStateLoadedWithProblems {
            problem("Build file 'build.gradle': line 6: invocation of 'Task.project' at execution time is unsupported.")
        }
    }
}
