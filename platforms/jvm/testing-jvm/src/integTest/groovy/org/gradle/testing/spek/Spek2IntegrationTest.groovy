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

package org.gradle.testing.spek

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult.TestFramework
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions

import static org.hamcrest.CoreMatchers.containsString

/**
 * Integration tests demonstrating use of the Spek2 testing framework.
 */
class Spek2IntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
    @Override
    TestFramework getTestFramework() {
        return TestFramework.SPEK
    }

    def setup() {
        buildFile <<"""
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${new KotlinGradlePluginVersions().latest}"
            }

            ${mavenCentralRepository()}

            testing.suites.test {
                useJUnitJupiter()

                dependencies {
                    implementation("org.spekframework.spek2:spek-dsl-jvm:2.0.19")
                    runtimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.19")
                }

                targets.all {
                    testTask.configure {
                        options {
                            includeEngines("spek2")
                        }
                    }
                }
            }
        """
    }

    def "tests run and report correctly"() {
        given:
        file('src/test/kotlin/org/example/SimpleSpekTest.kt') << """
            package org.example

            import org.spekframework.spek2.Spek
            import org.spekframework.spek2.style.specification.describe

            object SimpleSpekTest : Spek({
                describe("a calculator") {
                    it("should add two numbers") {
                        val result = 1 + 1
                        assert(result == 2)
                    }

                    it("should subtract two numbers") {
                        val result = 5 - 3
                        assert(result == 2)
                    }

                    it("should divide two numbers") {
                        throw NotImplementedError("Not implemented yet")
                    }
                }
            })
        """

        when:
        fails("test")

        then:
        def result = resultsFor()
        result.testPath(":org.example.SimpleSpekTest").onlyRoot().assertChildCount(1, 1)
        result.testPath(":org.example.SimpleSpekTest:a calculator").onlyRoot().assertChildCount(3, 1)
        result.testPath(":org.example.SimpleSpekTest:a calculator:should add two numbers").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        result.testPath(":org.example.SimpleSpekTest:a calculator:should subtract two numbers").onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        result.testPath(":org.example.SimpleSpekTest:a calculator:should divide two numbers").onlyRoot().assertHasResult(TestResult.ResultType.FAILURE)
    }

    def "tests report output correctly"() {
        file('src/test/kotlin/org/example/SimpleSpekTest.kt') << """
            package org.example

            import org.spekframework.spek2.Spek
            import org.spekframework.spek2.style.specification.describe

            object Tests: Spek({
                beforeGroup {
                    println("> beforeGroup (Describe)")
                }
                describe("Describe Tests") {
                    beforeGroup {
                        println("> beforeGroup (Context)")
                    }
                    context("Context Tests") {
                        it("test1") {
                            println("> test1")
                        }

                        it("test2") {
                            println("> test2")
                        }
                    }
                    afterGroup {
                        println("> afterGroup (Context)")
                    }
                }
                afterGroup {
                    println("> afterGroup (Describe)")
                }
            })
        """

        when:
        succeeds("test")

        then:
        def result = resultsFor()
        result.testPath(":org.example.Tests").onlyRoot()
            .assertChildCount(1, 0)
            .assertStdout(containsString("beforeGroup (Describe)"))
            .assertStdout(containsString("afterGroup (Describe)"))
        result.testPath(":org.example.Tests:Describe Tests").onlyRoot()
            .assertChildCount(1, 0)
            .assertStdout(containsString("beforeGroup (Context)"))
            .assertStdout(containsString("afterGroup (Context)"))
        result.testPath(":org.example.Tests:Describe Tests:Context Tests").onlyRoot().assertChildCount(2, 0)
        result.testPath(":org.example.Tests:Describe Tests:Context Tests:test1").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(containsString("test1"))
        result.testPath(":org.example.Tests:Describe Tests:Context Tests:test2").onlyRoot()
            .assertHasResult(TestResult.ResultType.SUCCESS)
            .assertStdout(containsString("test2"))
    }
}
