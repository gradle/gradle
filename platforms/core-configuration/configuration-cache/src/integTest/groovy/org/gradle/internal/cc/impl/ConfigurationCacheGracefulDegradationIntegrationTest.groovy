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

    def configurationCache = newConfigurationCacheFixture()

    def "a task can require CC degradation"() {
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
        executed(":buildSrc:compileJava", ":buildSrc:foo", ":help")
    }

    def "depending on a CC degrading task from included build introduces CC degradation"() {
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

    def "a dependency task in #build build can require CC degradation for the non-root build"() {
        buildFile("$build/build.gradle", """
            ${taskWithInjectedDegradationController()}

            def fooTask = tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Because reasons" })
            }

            tasks.register("bar") {
                dependsOn(fooTask)
            }
        """)
        settingsFile """
            $settingsConfiguration
        """

        when:
        configurationCacheRun ":$build:bar"

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 0
            withIncompatibleTask(":$build:foo", "Because reasons.")
        }

        and:
        assertConfigurationCacheDegradation()

        where:
        build      | settingsConfiguration
        "buildSrc" | ""
        "included" | "include('included')"
    }

    def "no CC degradation if incompatible task is not presented in the task graph"() {
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

    def "ignore CC degradation requests at execution time"() {
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
        configurationCacheRun ":foo", "-d"

        then:
        configurationCache.assertStateStored()

        and:
        outputContains("Configuration cache degradation request of task :foo is ignored at execution time")
    }

    def "tasks instantiated during execution have degradation requests ignored"() {
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
        notExecuted ":a"
    }

    def "user code exceptions in degradation reasons evaluation are surfaced"() {
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(
                    task,
                    provider { throw new IllegalStateException("Reason evaluation failed!") }
                )
            }
        """

        when:
        configurationCacheFails ":foo"

        then:
        failureDescriptionContains("Reason evaluation failed!")
    }

    def "user code exceptions in task graph traversing are surfaced"() {
        given:
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                // add a request to ensure we need to verify whether tasks are scheduled
                getDegradationController().requireConfigurationCacheDegradation(task, provider { null })
            }

            gradle.addListener(new TaskExecutionGraphListener() {
                void graphPopulated(TaskExecutionGraph graph) {
                    throw new RuntimeException("Graph traversing failed!")
                }
            })
        """

        when:
        configurationCacheFails ":foo"

        then:
        failureDescriptionContains("Graph traversing failed!")
    }

    def "degradation controller is available in vintage"() {
        given:
        buildFile """
            ${taskWithInjectedDegradationController()}

            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Because reasons" })
                doLast {
                    println("Hello")
                }
            }
        """

        when:
        run ":foo"

        then:
        executed ":foo"
    }

    def "CC report link is present even when no problems were reported"() {
        given:
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Because reasons" })
                doLast {
                    println("Hello")
                }
            }
        """

        when:
        run ":foo", ENABLE_CLI_OPT // disable printing a report link by default

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        problems.assertResultConsoleSummaryHasNoProblems(result)
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 0
            withIncompatibleTask(":foo", "Because reasons.")
        }

        and:
        executed ":foo"
        assertConfigurationCacheDegradation()
    }

    def "provides no extra header for link if other problems are present"() {
        buildFile """
            ${taskWithInjectedDegradationController()}
            tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Because reasons" })
                doLast {
                    println("Hello")
                }
            }
            tasks.register("bar") { task ->
                doLast {
                    println(project.name)
                }
            }
        """

        when:
        configurationCacheRunLenient("foo", "bar")

        then:
        configurationCache.assertNoConfigurationCache()

        and:
        assertConfigurationCacheDegradation(true)
    }

    def "CC incompatible tasks and requested CC degradation are correctly reported"() {
        buildFile """
            ${taskWithInjectedDegradationController()}

            def fooTask = tasks.register("foo", DegradingTask) { task ->
                getDegradationController().requireConfigurationCacheDegradation(task, provider { "Because reasons" })
            }

            tasks.register("bar") {
                dependsOn(fooTask)
                notCompatibleWithConfigurationCache("Project access")
                doLast {
                    project.path
                }
            }
        """

        when:
        configurationCacheRun "bar"

        then:
        configurationCache.assertNoConfigurationCache()
        assertConfigurationCacheDegradation(true)

        and:
        executed(":foo", ":bar")

        and:
        problems.assertResultHasConsoleSummary(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 17: invocation of 'Task.project' at execution time is unsupported with the configuration cache")
        }
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 17: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":bar", "Project access.")
            withIncompatibleTask(":foo", "Because reasons.")
        }
    }

    private static String taskWithInjectedDegradationController() {
        """
            abstract class DegradingTask extends DefaultTask {
                @${Inject.name}
                abstract ${ConfigurationCacheDegradationController.name} getDegradationController()
            }
        """
    }

    private void assertConfigurationCacheDegradation(boolean hasOtherProblems = false) {
        def reportLinkPreamble = "Some tasks in this build are not compatible with the configuration cache."
        if (hasOtherProblems) {
            outputDoesNotContain(reportLinkPreamble)
        } else {
            outputContains(reportLinkPreamble)
        }
        postBuildOutputContains("Configuration cache disabled because incompatible task")
    }
}
