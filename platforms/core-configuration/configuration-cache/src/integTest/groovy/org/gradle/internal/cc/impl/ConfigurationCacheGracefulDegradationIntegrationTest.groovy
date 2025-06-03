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
            ${generateDegradationController()}
            tasks.register("a") { task ->
               degradation.requireConfigurationCacheDegradation(task, provider { "Project access" })
               doLast {
                   println("Project path is \${project.path}")
               }
            }
        """

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":a", "Project access.")
        }

        and:
        outputContains("Project path is :")
        postBuildOutputContains("""
Configuration caching disabled because degradation was requested.
- Incompatible tasks:
\t- task `:a` of type `org.gradle.api.DefaultTask`""")
    }

    def "a task can require CC degradation for multiple reasons"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${generateDegradationController()}
            tasks.register("a") { task ->
                def shouldAccessTaskProjectProvider = providers.systemProperty("accessTaskProject").map { Boolean.parseBoolean(it) }.orElse(false)
                def shouldAccessTaskDependenciesProvider = providers.systemProperty("accessTaskDependencies").map { Boolean.parseBoolean(it) }.orElse(false)

                degradation.requireConfigurationCacheDegradation(task, shouldAccessTaskProjectProvider.map { it ? "Project access" : null })
                degradation.requireConfigurationCacheDegradation(task, shouldAccessTaskDependenciesProvider.map { it ? "TaskDependencies access" : null })

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
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = expectedProblems.size()
            expectedProblems.forEach { withProblem(it) }
            withIncompatibleTask(":a", degradationReason)
        }

        where:
        args                                                          | expectedOutputs                                               | expectedProblems                                                                                                | degradationReason
        ["-DaccessTaskProject=true"]                                  | ["Task's project accessed!"]                                  | ["Build file 'build.gradle': line 10: invocation of 'Task.project' at execution time is unsupported."]          | "Project access."
        ["-DaccessTaskProject=true", "-DaccessTaskDependencies=true"] | ["Task's project accessed!", "Task's dependencies accessed!"] | ["Build file 'build.gradle': line 10: invocation of 'Task.project' at execution time is unsupported.",
                                                                                                                                         "Build file 'build.gradle': line 14: invocation of 'Task.taskDependencies' at execution time is unsupported."] | "Project access, TaskDependencies access."
    }

    def "CC problems in incompatible tasks are not hidden by CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${generateDegradationController()}
            tasks.register("foo") { task ->
                degradation.requireConfigurationCacheDegradation(task, provider { "Project access" })
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
        configurationCacheFails "foo", "bar"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertFailureHtmlReportHasProblems(failure) {
            totalProblemsCount = 2
            withProblem("Build file 'build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":foo", "Project access.")
        }
    }

    def "a task in included build can require CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("buildLogic/build.gradle", """
            ${generateDegradationController()}
            tasks.register("foo") { task ->
                degradation.requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Hello from included build \${project.path}"
                }
            }
        """)
        settingsFile """
            includeBuild("buildLogic")
        """

        when:
        configurationCacheRun ":buildLogic:foo"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'buildLogic/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":buildLogic:foo", "Project access.")
        }

        and:
        outputContains("Hello from included build :")
        postBuildOutputContains("""
Configuration caching disabled because degradation was requested.
- Incompatible tasks:
\t- task `:buildLogic:foo` of type `org.gradle.api.DefaultTask`""")
    }

    def "a buildSrc task that requires degradation does not impact CC"() {
        def configurationCache = newConfigurationCacheFixture()
        file("buildSrc/src/main/java/MyClass.java") << "class MyClass {}"
        buildFile("buildSrc/build.gradle", """
            ${generateDegradationController()}
            tasks.named("compileJava") { task ->
                degradation.requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Executing \${task}"
                }
            }
        """)
        buildFile("build.gradle", """
        task foo
        """)

        when:
        configurationCacheRun ":foo"

        then:
        result.assertTaskExecuted(":buildSrc:compileJava")
        result.assertTaskExecuted(":foo")
        configurationCache.assertStateStored()
        outputContains("Executing task ':buildSrc:compileJava'")
    }

    def "depending on a CC degrading task from included build introduces CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("buildLogic/build.gradle", """
            ${generateDegradationController()}
            tasks.register("foo") { task ->
                degradation.requireConfigurationCacheDegradation(task, provider { "Project access" })
                doLast {
                    println "Hello from included build \${project.path}"
                }
            }
        """)
        settingsFile """
            includeBuild("buildLogic")
        """
        buildFile """
            tasks.register("bar") {
                dependsOn gradle.includedBuild("buildLogic").task(":foo")
                doLast {
                    println "Hello from root build"
                }
            }
        """

        when:
        configurationCacheRun "bar"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertResultHtmlReportHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'buildLogic/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":buildLogic:foo", "Project access.")
        }

        and:
        outputContains("Hello from included build :")
        outputContains("Hello from root build")
        postBuildOutputContains("""
Configuration caching disabled because degradation was requested.
- Incompatible tasks:
\t- task `:buildLogic:foo` of type `org.gradle.api.DefaultTask`""")
    }

    def "no CC degradation if incompatible task is not presented in the task graph"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            ${generateDegradationController()}
            tasks.register("a") { task ->
                degradation.requireConfigurationCacheDegradation(task, provider { "Project access" })
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

    private static String generateDegradationController() {
        return """
            interface DegradationService {
                @${Inject.name}
                ${ConfigurationCacheDegradationController.name} getController()
            }
            def degradation = objects.newInstance(DegradationService).controller
        """
    }
}
