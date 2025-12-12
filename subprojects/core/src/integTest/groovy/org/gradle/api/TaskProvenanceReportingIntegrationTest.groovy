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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskProvenanceReportingIntegrationTest extends AbstractIntegrationSpec {

    def "can report task provenance when registered in project"() {
        given:
        settingsFile """
            includeBuild("included")
            include(":lib")
        """
        buildFile("included/settings.gradle", "")
        buildFile("buildSrc/build.gradle", "")
        buildFile("included/build.gradle", "")
        buildFile("lib/build.gradle", "")

        buildFile(buildScriptPath, """
            tasks.register('foo') {
              doLast {
                    throw new RuntimeException("Failure!")
              }
            }
        """
        )

        when:
        fails task

        then:
        failureDescriptionContains expectedFailureDescription

        where:
        buildScriptPath         | task           | expectedFailureDescription
        "buildSrc/build.gradle" | "buildSrc:foo" | "Execution failed for task ':buildSrc:foo' created by build file 'buildSrc/build.gradle'"
        "included/build.gradle" | "included:foo" | "Execution failed for task ':included:foo' created by build file 'included/build.gradle'"
        "lib/build.gradle"      | "foo"          | "Execution failed for task ':lib:foo' created by build file 'lib/build.gradle'"
    }

    def "can read task user code source"() {
        given:
        buildFile """
            task foo {
                doLast {
                    println "Hello from \${userCodeSource.displayName}"
                }
            }
        """

        when:
        def result = run("foo")

        then:
        outputContains("Hello from build file 'build.gradle'")
    }

    def "can read task user code source for task registered by plugin"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.tasks.register("foo") {
                        doLast {
                            println "Hello from \${userCodeSource.displayName}"
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        def result = run("foo")

        then:
        outputContains("Hello from plugin class 'MyPlugin'")
    }

    def "can read task user code source for task registered by plugin in afterEvaluate"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate { p ->
                        p.tasks.register("foo") {
                            doLast {
                                println "Hello from \${userCodeSource.displayName}"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        def result = run("foo")

        then:
        outputContains("Hello from plugin class 'MyPlugin'")
    }

    def "task fails in afterEvaluate"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate { p ->
                        p.tasks.register("foo") {
                            doLast {
                                throw new RuntimeException("foo")
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' created by plugin class 'MyPlugin'.")
    }

    def "task fails in afterEvaluate from plugin applied by other plugin"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate { p ->
                        p.tasks.register("foo") {
                            doLast {
                                throw new RuntimeException("foo")
                            }
                        }
                    }
                }
            }

            class MyOtherPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.pluginManager.apply(MyPlugin.class)
                }
            }

            apply plugin: MyOtherPlugin
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' created by plugin class 'MyPlugin'.")
    }

    def "task registered in project fails"() {
        given:
        buildFile """
            tasks.register("foo") {
                doLast {
                    throw new RuntimeException("foo")
                }
            }
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' created by build file 'build.gradle'")
    }

    def "task registered in settings fails"() {
        given:
        settingsFile """
            gradle.rootProject {
                tasks.register("foo") {
                    doLast {
                        throw new RuntimeException("foo")
                    }
                }
            }
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' created by settings file 'settings.gradle'")
    }


    def "task registered in provider fails"() {
        given:
        buildFile """
            def myProvider = provider {
                tasks.register("foo") {
                    doLast {
                        throw new RuntimeException("foo")
                    }
                }
            }

            myProvider.get()
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' created by build file 'build.gradle'")
    }

    def "built in task fails"() {
        given:
        buildFile """
            help {
                doLast {
                    throw new RuntimeException("help")
                }
            }
        """

        when:
        fails("help")

        then:
        failureDescriptionContains("Execution failed for task ':help' created by plugin 'org.gradle.help-tasks'")
    }

    // Test basic case: plugin registers a task, task fails at runtime, exception blames plugin
    // Same, but plubin registers task in afterEvaluate
    // Same, but plugin applies another plugin in afterEvaluate, which registers a task
    // Check a built in task's context (init, help)
    // Test task creation by rules
}
