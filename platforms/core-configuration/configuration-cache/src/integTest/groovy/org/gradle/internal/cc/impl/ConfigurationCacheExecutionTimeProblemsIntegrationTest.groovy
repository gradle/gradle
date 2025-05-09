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
import spock.lang.Issue

class ConfigurationCacheExecutionTimeProblemsIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "task fails due to #invocation access during execution"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        buildFile << """
            abstract class MyTask extends DefaultTask {
                @TaskAction
                def action() {
                    println($code)
                }
            }

            class MyAction implements Action<Task> {
                void execute(Task task) {
                    task.$code
                }
            }

            tasks.register("a", MyTask)
            tasks.register("b", MyTask) {
                doLast(new MyAction())
            }
            tasks.register("c") {
                doFirst(new MyAction())
            }
            tasks.register("d") {
                doFirst { $code }
            }
        """

        when: "running a task with a problem in task action"
        configurationCacheFails "a"

        then:
        failureDescriptionStartsWith("Execution failed for task ':a'.")
        failureCauseContains("Invocation of '$invocation' by task ':a' at execution time is unsupported.")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            problem "Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported."
        }

        when: "running a task with a problem in custom doLast Action"
        configurationCacheFails "b"

        then:
        failureDescriptionStartsWith("Execution failed for task ':b'.")
        failureCauseContains("Invocation of '$invocation' by task ':b' at execution time is unsupported.")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            problem "Build file 'build.gradle': line 5: invocation of '$invocation' at execution time is unsupported."
        }

        when: "running a task with a problem in custom doFirst Action"
        configurationCacheFails "c"

        then:
        failureDescriptionStartsWith("Execution failed for task ':c'.")
        failureCauseContains("Invocation of '$invocation' by task ':c' at execution time is unsupported.")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            problem "Build file 'build.gradle': line 11: invocation of '$invocation' at execution time is unsupported."
        }

        when: "running a task with a problem in doLast lambda"
        configurationCacheFails "d"

        then:
        failureDescriptionStartsWith("Execution failed for task ':d'.")
        failureCauseContains("Invocation of '$invocation' by task ':d' at execution time is unsupported.")

        and:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            problem "Build file 'build.gradle': line 23: invocation of '$invocation' at execution time is unsupported."
        }

        where:
        invocation              | code
        'Task.project'          | 'project.name'
        'Task.dependsOn'        | 'dependsOn'
        'Task.taskDependencies' | 'taskDependencies'
    }

    @Issue("https://github.com/gradle/gradle/issues/32542")
    def "cacheable task fails on execution time problem"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        javaFile "src/main/java/Main.java", """public class Main {}"""

        buildFile """
            plugins { id("java") }

            version = "foo-version"
            tasks.compileJava { // using a cacheable task
                doLast {
                    println("version:" + project.version.toString())
                }
            }
        """

        when:
        configurationCacheFails "compileJava"

        then:
        configurationCache.assertStateStoredAndDiscarded {
            hasStoreFailure = false
            problem "Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported."
        }

        and:
        failureDescriptionStartsWith("Execution failed for task ':compileJava'.")
        failureCauseContains("Invocation of 'Task.project' by task ':compileJava' at execution time is unsupported.")

        and:
        // TODO: currently the build completes with 2 failures: (1) task failure, and (2) the CC failure
        // > 1 problem was found storing the configuration cache.
        // > - Build file 'build.gradle': line 7: invocation of 'Task.project' at execution time is unsupported.
        // ^ this seems like misleading failure description
        failure.assertHasFailures(2)
    }

}
