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

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Issue

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
    }

    def "does not run task when dependency in another build fails"() {
        given:
        dependency("org.test:buildB:1.0")
        buildB.file("src/main/java/B.java") << """
            broken!
        """
        buildA.buildFile << """
            task other {
                dependsOn configurations.compileClasspath
            }
            classes.dependsOn other
        """
        buildA.file("src/main/java/A.java") << """
            class A { }
        """

        when:
        fails(buildA, "assemble")

        then:
        result.assertTaskExecuted(":buildB:compileJava")
        result.assertTaskNotExecuted(":compileJava")
        result.assertTaskNotExecuted(":classes")
        result.assertTaskNotExecuted(":jar")
        result.assertTaskNotExecuted(":other")
        result.assertTaskNotExecuted(":assemble")
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':buildB:compileJava'.")
    }

    @Issue("https://github.com/gradle/gradle/issues/5714")
    def "build fails when finalizer task in included build that is not a dependency of any other task fails"() {
        given:
        dependency("org.test:buildB:1.0")
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
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
    }

    def "does not compile build script when build script classpath cannot be built"() {
        given:
        buildB.file("src/main/java/B.java") << """
            broken!
        """
        buildA.buildFile << """
            buildscript {
                dependencies { classpath "org.test:buildB:1.0" }
            }
            new B()
        """

        when:
        fails(buildA, "help")

        then:
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':buildB:compileJava'.")
        failure.assertHasCause("Compilation failed; see the compiler output below.")
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
        dependency("org.test:buildB:1.0")
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
        failure.assertHasFailures(2)
        failure.assertHasDescription("Execution failed for task ':broken'.")
        failure.assertHasDescription("Execution failed for task ':buildB:broken'.")
    }

    def "build fails when task in root build fails"() {
        buildA.buildFile << """
            task broken {
                doLast {
                    throw new RuntimeException("broken")
                }
            }
            processResources.dependsOn(broken)
        """
        dependency("org.test:buildB:1.0")

        when:
        fails(buildA, "assemble")

        then:
        result.assertTaskExecuted(":broken")
        failure.assertHasFailures(1)
        failure.assertHasDescription("Execution failed for task ':broken'.")
    }
}
