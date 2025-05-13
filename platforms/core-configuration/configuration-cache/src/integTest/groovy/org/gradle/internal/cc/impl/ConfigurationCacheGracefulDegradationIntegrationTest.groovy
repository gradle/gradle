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

class ConfigurationCacheGracefulDegradationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can declare applied plugin as CC incompatible"() {
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
            gradle.requireConfigurationCacheDegradationIf("Foo plugin isn't CC compatible") { true }
            plugins.apply("foo")
        """

        when:
        configurationCacheRun "help"

        then:
        outputContains("Build finished callback from foo plugin")
        postBuildOutputContains("build file 'build.gradle': Foo plugin isn't CC compatible")
    }

    def "a plugin requesting graceful degradation won't hide an incompatible plugin's problems"() {
        def fixture = new ConfigurationCacheFixture(this)
        buildFile("buildSrc/src/main/groovy/plugin1.gradle", """
            tasks.register("degrading") {
                gradle.requireConfigurationCacheDegradationIf("Task isn't CC compatible") { true }
            }
        """)
        buildFile("buildSrc/src/main/groovy/plugin2.gradle", """
            tasks.register("incompatible") {
                doLast {
                    println("Hello from incompatible: \${project.path}")
                }
            }
        """)
        buildFile("buildSrc/build.gradle", """
            plugins {
                id("groovy-gradle-plugin")
            }
        """)
        buildFile """
            plugins.apply("plugin1")
            plugins.apply("plugin2")
        """

        when:
        configurationCacheFails "incompatible"

        then:
        fixture.configurationCacheBuildOperations.assertStateStored()
        problems.assertFailureHasProblems(failure) {
            totalProblemsCount = 1
            withProblem("Plugin 'plugin2': invocation of 'Task.project' at execution time is unsupported.")
        }

        when:
        configurationCacheRun "degrading"

        then:
        fixture.configurationCacheBuildOperations.assertNoConfigurationCache()

        when:
        configurationCacheFails "degrading", "incompatible"

        then:
        fixture.configurationCacheBuildOperations.assertNoConfigurationCache()
        //TODO-RC we should see problems here though
    }

    def "cache should be stored if a task that needs degradation is not requested"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register("a") {
                gradle.requireConfigurationCacheDegradationIf("Task isn't CC compatible") { true }
            }
            tasks.register("b") {
                gradle.requireConfigurationCacheDegradationIf("Task is CC compatible") { false }
                doLast {
                    println "Hello from B"
                }
            }
        """

        when:
        configurationCacheRun "b"

        then:
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "a"

        then:
        configurationCache.assertNoConfigurationCache()

    }

    def "should report problems but not fail build if CC-incompatible task requests degradation"() {
        def configurationCache = newConfigurationCacheFixture()
        buildFile """
            tasks.register("degrading") { task ->
                gradle.requireConfigurationCacheDegradationIf("Task is not CC compatible") { true }
                doLast {
                    println "Hello from \${task.name}"
                    println "This is legal in vintage: \${project.name}"
                }
            }
        """

        when:
        //TODO-RC this should not fail, but does
        configurationCacheRun "degrading"

        then:
        configurationCache.assertNoConfigurationCache()

        //TODO-RC we should have a CC report and assert expected problems
    }
}
