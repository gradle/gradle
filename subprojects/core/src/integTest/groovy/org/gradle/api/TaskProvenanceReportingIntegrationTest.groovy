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
import org.gradle.test.fixtures.dsl.GradleDsl

/**
 * Integration tests for task provenance reporting in task failure messages.
 */
class TaskProvenanceReportingIntegrationTest extends AbstractIntegrationSpec {
    def "can report task provenance when registered in #buildScriptPath"() {
        given:
        settingsFile("""
            includeBuild("included")
            include(":lib")
        """)
        settingsFile("included/settings.gradle", """
            include(':otherLib')
        """)

        buildFile("included/build.gradle", "")
        buildFile("included/otherLib/build.gradle", "")
        buildFile("buildSrc/build.gradle", "")
        buildFile("lib/build.gradle", "")
        buildFile("build.gradle", "")

        and:
        buildFile(buildScriptPath, """
            tasks.register('foo') {
                doLast {
                    throw new RuntimeException("Failure!")
                }
            }
        """
        )

        when:
        fails(task)

        then:
        failureDescriptionContains(expectedFailureDescription)
        failureCauseContains("Failure!")

        where:
        buildScriptPath                 | task                      | expectedFailureDescription
        "build.gradle"                  | "foo"                     | "Execution failed for task ':foo' (registered in build file 'build.gradle')."
        "lib/build.gradle"              | "foo"                     | "Execution failed for task ':lib:foo' (registered in build file 'lib/build.gradle')."
        "buildSrc/build.gradle"         | "buildSrc:foo"            | "Execution failed for task ':buildSrc:foo' (registered in build file 'buildSrc/build.gradle')."
        "included/build.gradle"         | "included:foo"            | "Execution failed for task ':included:foo' (registered in build file 'included/build.gradle')."
        "included/otherLib/build.gradle"| "included:otherLib:foo"   | "Execution failed for task ':included:otherLib:foo' (registered in build file 'included/otherLib/build.gradle')."
    }

    def "can report task provenance when registered in #buildScriptPath with Kotlin DSL"() {
        given:
        settingsKotlinFile.append("""
            includeBuild("included")
            include(":lib")
        """)
        kotlinFile("included/settings.gradle.kts", """
            include(":otherLib")
        """)

        kotlinFile("included/build.gradle.kts", "")
        kotlinFile("included/otherLib/build.gradle.kts", "")
        kotlinFile("buildSrc/build.gradle.kts", "")
        kotlinFile("lib/build.gradle.kts", "")
        kotlinFile("build.gradle.kts", "")

        and:
        file(buildScriptPath).append("""
            tasks.register("foo") {
                doLast {
                    throw RuntimeException("Failure!")
                }
            }
        """)

        when:
        fails(task)

        then:
        failureDescriptionContains(expectedFailureDescription)
        failureCauseContains("Failure!")

        where:
        buildScriptPath                     | task                      | expectedFailureDescription
        "build.gradle.kts"                  | "foo"                     | "Execution failed for task ':foo' (registered in build file 'build.gradle.kts')."
        "lib/build.gradle.kts"              | "foo"                     | "Execution failed for task ':lib:foo' (registered in build file 'lib/build.gradle.kts')."
        "buildSrc/build.gradle.kts"         | "buildSrc:foo"            | "Execution failed for task ':buildSrc:foo' (registered in build file 'buildSrc/build.gradle.kts')."
        "included/build.gradle.kts"         | "included:foo"            | "Execution failed for task ':included:foo' (registered in build file 'included/build.gradle.kts')."
        "included/otherLib/build.gradle.kts"| "included:otherLib:foo"   | "Execution failed for task ':included:otherLib:foo' (registered in build file 'included/otherLib/build.gradle.kts')."
    }

    def "task added in afterEvaluate reports provenance"() {
        given:
        buildFile """
            project.afterEvaluate { p ->
                p.tasks.register("foo") {
                    doLast {
                        throw new RuntimeException("Failure!")
                    }
                }
            }
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' (registered in build file 'build.gradle').")
        failureCauseContains("Failure!")
    }

    def "task added by plugin in afterEvaluate reports provenance"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate { p ->
                        p.tasks.register("foo") {
                            doLast {
                                throw new RuntimeException("Failure!")
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
        failureDescriptionContains("Execution failed for task ':foo' (registered by plugin class 'MyPlugin').")
        failureCauseContains("Failure!")
    }

    def "task added by transitively applied plugin in afterEvaluate reports provenance"() {
        given:
        buildFile """
            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {
                    project.afterEvaluate { p ->
                        p.tasks.register("foo") {
                            doLast {
                                throw new RuntimeException("Failure!")
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
        failureDescriptionContains("Execution failed for task ':foo' (registered by plugin class 'MyPlugin').")
        failureCauseContains("Failure!")
    }

    def "task registered in settings fails"() {
        given:
        settingsFile """
            gradle.rootProject {
                tasks.register("foo") {
                    doLast {
                        throw new RuntimeException("Failure!")
                    }
                }
            }
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' (registered in settings file 'settings.gradle').")
        failureCauseContains("Failure!")
    }

    def "task registered in settings fails with Kotlin DSL"() {
        given:
        settingsKotlinFile.append("""
            gradle.rootProject {
                tasks.register("foo") {
                    doLast {
                        throw RuntimeException("Failure!")
                    }
                }
            }
        """)

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' (registered in settings file 'settings.gradle.kts').")
        failureCauseContains("Failure!")
    }

    def "task registered in included build settings fails"() {
        given:
        settingsFile("""
            includeBuild("included")
        """)
        settingsFile("included/settings.gradle", """
            gradle.rootProject {
                tasks.register("foo") {
                    doLast {
                        throw new RuntimeException("Failure!")
                    }
                }
            }
        """)

        when:
        fails(":included:foo")

        then:
        failureDescriptionContains("Execution failed for task ':included:foo' (registered in settings file 'included/settings.gradle').")
        failureCauseContains("Failure!")
    }

    def "task registered in provider fails"() {
        given:
        buildFile """
            def myProvider = provider {
                tasks.register("foo") {
                    doLast {
                        throw new RuntimeException("Failure!")
                    }
                }
            }

            myProvider.get()
        """

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' (registered in build file 'build.gradle').")
        failureCauseContains("Failure!")
    }

    def "built in task fails"() {
        given:
        buildFile """
            help {
                doLast {
                    throw new RuntimeException("Failure!")
                }
            }
        """

        when:
        fails("help")

        then:
        failureDescriptionContains("Execution failed for task ':help' (registered by plugin 'org.gradle.help-tasks').")
        failureCauseContains("Failure!")
    }

    def "task registered in pre-compiled script plugin reports provenance"() {
        given:
        buildFile("buildSrc/src/main/groovy/myplugin.gradle", """
            tasks.register("foo") {
                doLast {
                    throw new RuntimeException("Failure!")
                }
            }
        """)

        buildFile("buildSrc/build.gradle", """
            plugins {
                id 'groovy-gradle-plugin'
            }
        """)

        buildFile("""
            plugins {
                id 'myplugin'
            }
        """)

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' (registered by plugin 'myplugin').")
        failureCauseContains("Failure!")
    }

    def "task registered in pre-compiled script plugin reports provenance with Kotlin DSL"() {
        given:
        kotlinFile("buildSrc/src/main/kotlin/myplugin.gradle.kts", """
            tasks.register("foo") {
                doLast {
                    throw RuntimeException("Failure!")
                }
            }
        """)

        kotlinFile("buildSrc/build.gradle.kts", """
            plugins {
                `kotlin-dsl`
            }

            ${mavenCentralRepository(GradleDsl.KOTLIN)}
        """)

        buildKotlinFile("""
            plugins {
                id("myplugin")
            }
        """)

        when:
        fails("foo")

        then:
        failureDescriptionContains("Execution failed for task ':foo' (registered by plugin 'myplugin').")
        failureCauseContains("Failure!")
    }
}
