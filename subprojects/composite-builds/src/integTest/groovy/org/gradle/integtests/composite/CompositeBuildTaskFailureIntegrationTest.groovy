/*
 * Copyright 2018 the original author or authors.
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

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule


class CompositeBuildTaskFailureIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB

    def setup() {
        buildB = multiProjectBuild("buildB", []) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB
        dependency("org.test:buildB:1.0")
    }

    @NotYetImplemented
    def "does not run tasks when dependency fails"() {
        given:
        buildB.file("src/main/java/B.java") << """
            broken!
        """
        buildA.file("src/main/java/A.java") << """
            class A { }
        """

        when:
        fails(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildB:compileJava")
        failure.assertTaskNotExecuted(":compileJava")
        failure.assertTaskNotExecuted(":classes")
        failure.assertTaskNotExecuted(":jar")
        failure.assertTaskNotExecuted(":assemble")
        failure.assertHasDescription("Execution failed for task ':buildB:compileJava'.")
    }

    def "build fails when finalizer task that is not a dependency of any other task fails"() {
        given:
        buildB.buildFile << """
            task broken {
                doLast { throw new RuntimeException("broken") }
            }
            jar.finalizedBy(broken)
        """

        when:
        fails(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildB:broken")
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
    }

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    def "build fails when tasks in multiple builds fails"() {
        given:
        server.start()

        buildA.buildFile << """
            task broken {
                doLast { 
                    ${server.callFromBuild('buildA')}
                    throw new RuntimeException("broken") 
                }
            }
            processResources.dependsOn(broken)
        """
        buildB.buildFile << """
            task broken {
                doLast { 
                    ${server.callFromBuild('buildB')}
                    throw new RuntimeException("broken") 
                }
            }
            processResources.dependsOn(broken)
        """
        server.expectConcurrent("buildA", "buildB")

        when:
        fails(buildA, "assemble")

        then:
        result.assertTaskExecuted(":broken")
        result.assertTaskExecuted(":buildB:broken")
        failure.assertHasFailureSummary("Build completed with 2 failures.")
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
    }
}
