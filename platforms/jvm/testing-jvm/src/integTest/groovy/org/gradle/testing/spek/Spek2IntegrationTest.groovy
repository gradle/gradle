/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.tasks.testing.operations.ExecuteTestBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.internal.operations.trace.BuildOperationRecord

class Spek2IntegrationTest extends AbstractIntegrationSpec {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "Spek2 tests report class names correctly in TestStarted events"() {
        given:
        buildFile """
            plugins {
                id("org.jetbrains.kotlin.jvm") version "${new KotlinGradlePluginVersions().latest}"
            }

            ${mavenCentralRepository()}

            dependencies {
                testImplementation("org.spekframework.spek2:spek-dsl-jvm:2.0.19")
                testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:2.0.19")
                testRuntimeOnly("org.junit.platform:junit-platform-launcher")
            }

            tasks.named("test") {
                useJUnitPlatform {
                    includeEngines("spek2")
                }
            }
        """

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
                }
            })
        """

        when:
        succeeds("test")

        then:
        def testOps = operations.all(ExecuteTestBuildOperationType) { true }
        def testDescriptors = testOps.collect { it.details.testDescriptor }

        // Verify that no test descriptor has an empty string as name or className
        testDescriptors.each { descriptor ->
            assert descriptor.name != null && descriptor.name != "" : "Test descriptor should not have empty string as name"
            if (descriptor.className != null) {
                assert descriptor.className != "" : "Test descriptor should not have empty string as className: ${descriptor.name}"
            }
        }

        // Verify we have test operations for the Spek test
        def spekTestOps = testOps.findAll { 
            it.details.testDescriptor.name?.contains("SimpleSpekTest") || 
            it.details.testDescriptor.className?.contains("SimpleSpekTest")
        }
        assert spekTestOps.size() > 0 : "Should have test operations for SimpleSpekTest"

        // Verify individual test methods have proper className
        def individualTests = testOps.findAll { !it.details.testDescriptor.composite }
        individualTests.each { testOp ->
            def descriptor = testOp.details.testDescriptor
            assert descriptor.name != null && descriptor.name != "" : "Individual test should have non-empty name"
            if (descriptor.className != null) {
                assert descriptor.className != "" : "Individual test '${descriptor.name}' should not have empty className"
            }
        }
    }
}
