/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.junit.Rule

@Requires(IntegTestPreconditions.NotParallelExecutor)
class CompositeBuildParallelIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    @Rule BlockingHttpServer server = new BlockingHttpServer()

    def "works when number of included builds exceeds max-workers --max-workers=#maxWorkers"() {
        def totalIncludedBuilds = 5*maxWorkers
        buildA.buildFile << """
            task delegate {
                dependsOn gradle.includedBuilds*.task(":someTask")
            }
        """
        (1..totalIncludedBuilds).each {
            includedBuilds << singleProjectBuild("included$it") {
                buildFile << """
                    task someTask {
                        doLast {
                            Thread.sleep(100)
                        }
                    }
                """
            }
        }
        expect:
        execute(buildA, "delegate", "--parallel", "--max-workers=$maxWorkers")
        operations.assertConcurrentOperationsDoNotExceed(ExecuteTaskBuildOperationType, maxWorkers, maxWorkers != 1)

        where:
        maxWorkers << [ 1, 2, 4 ]
    }

    def "can build transitive dependency chain with --max-workers == 1"() {
        def previousBuild = buildA
        ['buildB', 'buildC', 'buildD'].each { buildName ->
            def build = singleProjectBuild(buildName) {
                buildFile << """
                    apply plugin: 'java'
"""
            }
            dependency previousBuild, "org.test:${buildName}:1.0"
            includedBuilds << build
            previousBuild = build
        }

        expect:
        execute(buildA, "jar", "--max-workers=1")
    }

    def "constructs included build artifacts in parallel"() {
        given:
        server.start()

        when:
        def included = ['buildB', 'buildC', 'buildD']
        included.each { buildName ->
            def build = singleProjectBuild(buildName) {
                buildFile << """
                    apply plugin: 'java'
                    compileJava.doLast {
                        ${server.callFromBuild(buildName)}
                    }
                """
            }
            dependency "org.test:${buildName}:1.0"
            includeBuild build
        }

        server.expectConcurrent(included)

        then:
        execute(buildA, "jar", "--max-workers=4")
    }

    def "constructs included build artifacts in parallel with multi-project included build"() {
        given:
        def maxWorkers = 4
        server.start()
        when:
        def expectedCalls = []
        includedBuilds << multiProjectBuild("buildB", (1..maxWorkers).collect { "sub" + it }) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                    compileJava {
                        def projectName = project.name
                        doLast {
                            ${server.callFromBuildUsingExpression('projectName')}
                        }
                    }
                }
            """
        }
        (1..maxWorkers).each {
            dependency "org.test:sub${it}:1.0"
            expectedCalls << 'sub' + it
        }

        server.expectConcurrent(expectedCalls)

        then:
        execute(buildA, "jar", "--parallel", "--max-workers=$maxWorkers")
        operations.assertConcurrentOperationsDoNotExceed(ExecuteTaskBuildOperationType, maxWorkers, true)
    }
}
