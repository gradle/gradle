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

class ConfigurationCacheGracefulDegradationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can declare applied plugin as CC incompatible"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("buildSrc/src/main/groovy/foo.gradle", """
            gradle.addBuildListener(new BuildListener() {
                @Override
                void settingsEvaluated(Settings settings){}
                @Override
                void projectsLoaded(Gradle gradle){}
                @Override
                void projectsEvaluated(Gradle gradle){}
                @Override
                void buildFinished(BuildResult result){
                    println("Build finished callback from foo plugin")
                }
                })
        """)

        buildFile("buildSrc/build.gradle", """
            plugins {
                id("groovy-gradle-plugin")
            }
        """)

        buildFile """
            gradle.requireConfigurationCacheDegradation("Foo plugin isn't CC compatible", provider { true })
            plugins.apply("foo")
        """

        when:
        configurationCacheRun "help"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 3: registration of listener on 'Gradle.addBuildListener' is unsupported")
        }

        and:
        outputContains("Build finished callback from foo plugin")
        postBuildOutputContains("""
Configuration cache entry discarded because degradation was requested by:
- build file 'build.gradle': Foo plugin isn't CC compatible""")
    }

    def "a task can require CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register("a") {
               requireConfigurationCacheDegradation("Project access", provider { true })
               doLast {
                   println("Project path is \${project.path}")
               }
            }
        """

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":a", "Project access.")
        }

        and:
        outputContains("Project path is :")
        postBuildOutputContains("""
Configuration cache entry discarded because degradation was requested by:
- task `:a` of type `org.gradle.api.DefaultTask`: Project access""")
    }

    def "a task can require CC degradation for multiple reasons"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register("a") {
                def shouldAccessTaskProjectProvider = providers.systemProperty("accessTaskProject").map { Boolean.parseBoolean(it) }.orElse(false)
                def shouldAccessTaskDependenciesProvider = providers.systemProperty("accessTaskDependencies").map { Boolean.parseBoolean(it)}.orElse(false)
                requireConfigurationCacheDegradation("Project access", shouldAccessTaskProjectProvider)
                requireConfigurationCacheDegradation("TaskDependencies access", shouldAccessTaskDependenciesProvider)

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
        problems.assertResultHasProblems(result) {
            totalProblemsCount = expectedProblems.size()
            expectedProblems.forEach { withProblem(it) }
            withIncompatibleTask(":a", degradationReason)
        }

        and:
        expectedProblems.forEach { outputContains(it) }

        where:
        args                                                          | expectedOutputs                                               | expectedProblems                                                                                                | degradationReason
        ["-DaccessTaskProject=true"]                                  | ["Task's project accessed!"]                                  | ["Build file 'build.gradle': line 10: invocation of 'Task.project' at execution time is unsupported."]          | "Project access."
        ["-DaccessTaskProject=true", "-DaccessTaskDependencies=true"] | ["Task's project accessed!", "Task's dependencies accessed!"] | ["Build file 'build.gradle': line 10: invocation of 'Task.project' at execution time is unsupported.",
                                                                                                                                         "Build file 'build.gradle': line 14: invocation of 'Task.taskDependencies' at execution time is unsupported."] | "Project access, TaskDependencies access."
    }

    def "CC problems in incompatible tasks are not hidden by CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register("foo") {
                requireConfigurationCacheDegradation("Project access", provider { true })
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
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 2
            withProblem("Build file 'build.gradle': line 11: invocation of 'Task.project' at execution time is unsupported.")
            withProblem("Build file 'build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":foo", "Project access.")
        }
    }

    def "a task in included build can require CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("buildLogic/build.gradle", """
            tasks.register("foo") {
                requireConfigurationCacheDegradation("Project access", provider { true })
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
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'buildLogic/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":buildLogic:foo", "Project access.")
        }

        and:
        outputContains("Hello from included build :")
        postBuildOutputContains("""
Configuration cache entry discarded because degradation was requested by:
- task `:buildLogic:foo` of type `org.gradle.api.DefaultTask`: Project access""")
    }

    def "depending on a CC degrading task from included build introduces CC degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("buildLogic/build.gradle", """
            tasks.register("foo") {
                requireConfigurationCacheDegradation("Project access", provider { true })
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
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 1
            withProblem("Build file 'buildLogic/build.gradle': line 5: invocation of 'Task.project' at execution time is unsupported.")
            withIncompatibleTask(":buildLogic:foo", "Project access.")
        }

        and:
        outputContains("Hello from included build :")
        outputContains("Hello from root build")
        postBuildOutputContains("""
Configuration cache entry discarded because degradation was requested by:
- task `:buildLogic:foo` of type `org.gradle.api.DefaultTask`: Project access""")
    }

    def "no CC degradation if incompatible task is not presented in the task graph"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register("a") {
                requireConfigurationCacheDegradation("Project access", provider { true })
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

    def "a plugin requesting СС degradation hides an incompatible plugin's problems"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile("buildSrc/src/main/groovy/degrading.gradle", """
            gradle.requireConfigurationCacheDegradation("Build listener registration", project.provider { true })
            gradle.addBuildListener(new BuildListener() {
                @Override
                void settingsEvaluated(Settings settings){}
                @Override
                void projectsLoaded(Gradle gradle){}
                @Override
                void projectsEvaluated(Gradle gradle){}
                @Override
                void buildFinished(BuildResult result){
                    println("Build finished callback from degrading plugin")
                }
                })
        """)
        buildFile("buildSrc/src/main/groovy/incompatible.gradle", """
            gradle.addBuildListener(new BuildListener() {
                @Override
                void settingsEvaluated(Settings settings){}
                @Override
                void projectsLoaded(Gradle gradle){}
                @Override
                void projectsEvaluated(Gradle gradle){}
                @Override
                void buildFinished(BuildResult result){
                    println("Build finished callback from incompatible plugin")
                }
            })
        """)
        buildFile("buildSrc/build.gradle", """
            plugins {
                id("groovy-gradle-plugin")
            }
        """)
        buildFile """
            plugins {
                id("degrading")
                id("incompatible")
            }
        """

        when:
        configurationCacheRun "help"

        then:
        configurationCache.assertNoConfigurationCache()
        problems.assertResultHasProblems(result) {
            totalProblemsCount = 2
            withProblem("Plugin 'degrading': registration of listener on 'Gradle.addBuildListener' is unsupported")
            withProblem("Plugin 'incompatible': registration of listener on 'Gradle.addBuildListener' is unsupported")
        }

        and:
        outputContains("Build finished callback from degrading plugin")
        outputContains("Build finished callback from incompatible plugin")
        postBuildOutputContains("""
Configuration cache entry discarded because degradation was requested by:
- plugin 'degrading': Build listener registration""")
    }
}
