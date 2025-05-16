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


import org.junit.Ignore

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
    //TODO: Add scenarios with included build tasks:
    //          1. Only task(degrading) of included build executed.
    //          2. Task(degrading) of included build used as a dependency for the task of the root build.
    //      Add scenario with tasks adding a several degradation requests
}
