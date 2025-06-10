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

import org.gradle.api.internal.ConfigurationCacheDegradationController

import javax.inject.Inject

class ConfigurationCacheGracefulDegradationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "a task can require CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("a", DegradingTask) { task ->
               getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
               doLast {
                   println("Project path is \${project.path}")
               }
            }
        """

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":a", "Project access.")
        }

        and:
        outputContains("Project path is :")
        assertConfigurationCacheDegradation()
    }

    def "a task can require CC degradation for multiple reasons"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("a", DegradingTask) { task ->
                def shouldAccessTaskProjectProvider = providers.systemProperty("accessTaskProject").map { Boolean.parseBoolean(it) }.orElse(false)
                def shouldAccessTaskDependenciesProvider = providers.systemProperty("accessTaskDependencies").map { Boolean.parseBoolean(it) }.orElse(false)

                getDegradationController().requireConfigurationCacheDegradation(task, shouldAccessTaskProjectProvider.map { it ? "Project access" : null })
                getDegradationController().requireConfigurationCacheDegradation(task, shouldAccessTaskDependenciesProvider.map { it ? "TaskDependencies access" : null })

                doLast {
                    if (shouldAccessTaskProjectProvider.get()) {
                        it.project
                        println "Task's project accessed!"
                    }
                    if (shouldAccessTaskDependenciesProvider.get()) {
                        it.taskDependencies
                        println "Task's dependencies accessed!"
                    }
                }
            }
        """

        when:
        configurationCacheRun("a", *args)

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = expectedProblems.size()
            expectedProblems.forEach { withProblem(it) }
            withIncompatibleTask(":a", degradationReason)
        }

        where:
        expectedProblems                                                                                                | _
        ["Build file 'build.gradle': line 10: invocation of 'Task.project' at execution time is unsupported."]          | _
        ["Build file 'build.gradle': line 10: invocation of 'Task.project' at execution time is unsupported.",
         "Build file 'build.gradle': line 14: invocation of 'Task.taskDependencies' at execution time is unsupported."] | _
        __
        args                                                          | expectedOutputs                                               | degradationReason
        ["-DaccessTaskProject=true"]                                  | ["Task's project accessed!"]                                  | "Project access."
        ["-DaccessTaskProject=true", "-DaccessTaskDependencies=true"] | ["Task's project accessed!", "Task's dependencies accessed!"] | "Project access, TaskDependencies access."
    }

    def "CC problems in warning mode are not hidden by CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Hello from foo \${project.path}"
                }
            }

            tasks.register("bar") {
                doLast {
                    println "Hello from bar \${project.path}"
                }
            }
        """

        when:
        configurationCacheRunLenient "foo", "bar"

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultHasConsoleSummary(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 17: invocation of 'Task.project' at execution time is unsupported with the configuration cache.")
        }
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 2
            withProblem("Build file 'build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 17: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":foo", "Project access.")
        }
    }

    def "a task in included build can require CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("included/build.gradle", """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Hello from included build \${project.path}"
                }
            }
        """)
        settingsFile """
            includeBuild("included")
        """

        when:
        configurationCacheRun ":included:foo"

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'included/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":included:foo", "Project access.")
        }

        and:
        outputContains("Hello from included build :")
        assertConfigurationCacheDegradation()
    }

    def "a buildSrc internal task that requires CC degradation does not introduce root build CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        file("buildSrc/src/main/java/MyClass.java") << "class MyClass {}"
        buildFile("buildSrc/build.gradle", """
            ${taskWithInjectedDegradationController()}
            def fooTask = tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Hello from foo \${project.path}"
                }
            }
            tasks.withType(JavaCompile).configureEach {
                dependsOn(fooTask)
            }
        """)

        when:
        configurationCacheRun "help"

        then:
        configurationCache.assertStateStored()

        and:
        result.assertTaskExecuted(":buildSrc:compileJava")
        result.assertTaskExecuted(":buildSrc:foo")
        result.assertTaskExecuted(":help")
    }

    def "depending on a CC degrading task from included build introduces CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("included/build.gradle", """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Hello from included build \${project.path}"
                }
            }
        """)
        settingsFile """
            includeBuild("included")
        """
        buildFile """
            tasks.register("bar") {
                dependsOn gradle.includedBuild("included").task(":foo")
                doLast {
                    println "Hello from root build"
                }
            }
        """

        when:
        configurationCacheRun "bar"

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'included/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":included:foo", "Project access.")
        }

        and:
        outputContains("Hello from included build :")
        outputContains("Hello from root build")
        assertConfigurationCacheDegradation()
    }

    def "no CC degradation if incompatible task is not presented in the task graph"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("a", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println("Project path is \${project.path}")
                }
            }

            tasks.register("b") {
                doLast {
                    println "Hello from B"
                }
            }

            tasks.all {
                println "\$it configured"
            }
        """

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateStored()
        outputContains("task ':a' configured")
        outputContains("task ':b' configured")
        outputContains("Hello from B")
    }

    def "cannot require CC degradation at execution time"() {
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                def reason = provider { "Misconfiguration!" }
                doLast {
                    getDegradationController().requireConfigurationCacheDegradation(task, reason)
                }
            }
        """

        when:
        configurationCacheFails ":foo"

        then:
        failureCauseContains("Configuration cache degradation requests are accepted only at configuration time")
    }

    def "tasks instantiated during execution have degradation requests ignored"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("a", DegradingTask) { task ->
                println("Should be configured")
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Project access" })
            }
        """

        when:
        // :tasks instantiates tasks instances at execution time
        configurationCacheRun ":tasks"

        then:
        configurationCache.assertStateStored()

        and:
        outputContains("Should be configured")
        result.assertTaskNotExecuted(":a")
    }

    private static String taskWithInjectedDegradationController() {
        """
            abstract class DegradingTask extends DefaultTask {
                @${Inject.name}
                abstract ${ConfigurationCacheDegradationController.name} getDegradationController()
            }
        """
    }

    private void assertConfigurationCacheDegradation() {
        postBuildOutputContains("Configuration caching disabled because incompatible task")
    }
}
