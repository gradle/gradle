/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.configurationcache


import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

class ConfigurationCacheParallelTaskIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    @Requires(value = IntegTestPreconditions.NotParallelExecutor, reason = """
Don't run in parallel mode, as the expectation for the setup build are incorrect and running in parallel
does not really make any difference to the coverage
""")
    def "runs tasks in different projects in parallel by default"() {
        server.start()

        given:
        createDirs("a", "b", "c")
        settingsFile << """
            include 'a', 'b', 'c'
        """
        buildFile << """
            class SlowTask extends DefaultTask {

                private final String projectName = project.name

                @TaskAction
                def go() {
                    ${server.callFromBuildUsingExpression("projectName")}
                }
            }

            subprojects {
                tasks.create('slow', SlowTask)
            }
            project(':a') {
                tasks.slow.dependsOn(project(':b').tasks.slow, project(':c').tasks.slow)
            }
        """

        expect:
        2.times {
            server.expectConcurrent("b", "c")
            server.expectConcurrent("a")
            configurationCacheRun "a:slow"
        }
    }

    @Requires(value = IntegTestPreconditions.NotParallelExecutor, reason = """
Don't run in parallel mode, as the expectation for the setup build are incorrect
It could potentially be worth running this in parallel mode to demonstrate the difference between
parallel and configuration cache
""")
    def "runs tasks in same project in parallel by default"() {
        server.start()

        given:
        buildFile << """
            class SlowTask extends DefaultTask {
                @TaskAction
                def go() {
                    ${server.callFromBuildUsingExpression("name")}
                }
            }
            tasks.create('b', SlowTask)
            tasks.create('c', SlowTask)
            tasks.create('a', SlowTask) {
                dependsOn('b', 'c')
            }
            tasks.create('d', SlowTask) {
                mustRunAfter('a')
            }
        """

        expect:
        2.times {
            server.expectConcurrent("b", "c")
            server.expectConcurrent("a")
            server.expectConcurrent("d")
            configurationCacheRun "a", "d"
        }
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "finalizer task dependencies from sibling project must run after finalized task dependencies"() {
        server.start()

        given:
        def configurationCache = newConfigurationCacheFixture()
        createDirs("finalized", "finalizer")
        settingsFile << """
            include 'finalized', 'finalizer'
        """
        buildFile << """
            class SlowTask extends DefaultTask {
                @TaskAction
                def go() {
                    ${server.callFromBuildUsingExpression("path")}
                }
            }
            project(':finalizer') {
                tasks.create('dep', SlowTask)
                tasks.create('task', SlowTask) {
                    dependsOn 'dep'
                }
            }
            project(':finalized') {
                tasks.create('dep', SlowTask)
                tasks.create('task', SlowTask) {
                    finalizedBy ':finalizer:task'
                    dependsOn 'dep'
                }
            }
        """

        expect: "unrequested finalizer dependencies not to run in parallel when storing the graph"
        [":finalized:dep", ":finalized:task", ":finalizer:dep", ":finalizer:task"].each {
            server.expectConcurrent(it)
        }
        configurationCacheRun ":finalized:task", "--parallel"
        configurationCache.assertStateStored()

        and: "unrequested finalizer dependencies not to run in parallel when loading the graph"
        [":finalized:dep", ":finalized:task", ":finalizer:dep", ":finalizer:task"].each {
            server.expectConcurrent(it)
        }
        configurationCacheRun ":finalized:task"
        configurationCache.assertStateLoaded()

        and: "requested finalizer dependencies to run in parallel when storing the graph with --parallel"
        server.expectConcurrent(":finalized:dep", ":finalizer:dep")
        [":finalized:task", ":finalizer:task"].each {
            server.expectConcurrent(it)
        }
        configurationCacheRun ":finalizer:dep", ":finalized:task", "--parallel"
        configurationCache.assertStateStored()

        and: "requested finalizer dependencies to run in parallel when loading the graph by default"
        server.expectConcurrent(":finalized:dep", ":finalizer:dep")
        [":finalized:task", ":finalizer:task"].each {
            server.expectConcurrent(it)
        }
        configurationCacheRun ":finalizer:dep", ":finalized:task"
        configurationCache.assertStateLoaded()
    }

    @Requires(IntegTestPreconditions.NotParallelExecutor)
    def "finalizer task dependencies must run after finalized task dependencies"() {
        server.start()

        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile << """
            class SlowTask extends DefaultTask {
                @TaskAction
                def go() {
                    ${server.callFromBuildUsingExpression("name")}
                }
            }
            tasks.create('finalizerDep', SlowTask)
            tasks.create('finalizer', SlowTask) {
                dependsOn 'finalizerDep'
            }
            tasks.create('finalizedDep', SlowTask)
            tasks.create('finalized', SlowTask) {
                finalizedBy 'finalizer'
                dependsOn 'finalizedDep'
            }
        """

        expect: "unrequested finalizer dependencies not to run in parallel"
        2.times {
            ["finalizedDep", "finalized", "finalizerDep", "finalizer"].each {
                server.expectConcurrent(it)
            }
            configurationCacheRun "finalized"
        }

        and: "requested finalizer dependencies to run in parallel"
        2.times {
            server.expectConcurrent("finalizerDep", "finalizedDep")
            ["finalized", "finalizer"].each {
                server.expectConcurrent(it)
            }
            configurationCacheRun "finalizerDep", "finalized"
        }
    }
}
