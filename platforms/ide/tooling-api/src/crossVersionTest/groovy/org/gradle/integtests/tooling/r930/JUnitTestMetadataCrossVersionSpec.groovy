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

package org.gradle.integtests.tooling.r930

import org.gradle.integtests.tooling.TestEventsFixture
import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType

@ToolingApiVersion(">=8.13")
@TargetGradleVersion(">=9.3.0")
class JUnitTestMetadataCrossVersionSpec extends ToolingApiSpecification implements TestEventsFixture {
    ProgressEvents events = ProgressEvents.create()

    @Override
    ProgressEvents getEvents() {
        return events
    }

    def setup() {
        buildFile << """
            plugins {
                id("java-library")
            }

            ${mavenCentralRepository()}

            testing {
                suites {
                    test {
                        useJUnitJupiter()
                    }
                }
            }
        """
    }

    def "receives test metadata from JUnit platform tests"() {
        file("src/test/java/com/example/ReportEntryTest.java").java """
            package com.example;

            import org.junit.jupiter.api.AfterAll;
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.TestReporter;

            public class ReportEntryTest {
                ReportEntryTest(TestReporter testReporter) {
                    testReporter.publishEntry("constructor", "value");
                }

                @BeforeEach
                public void beforeEach(TestReporter testReporter) {
                    testReporter.publishEntry("beforeEach", "value");
                }
                @AfterEach
                public void afterEach(TestReporter testReporter) {
                    testReporter.publishEntry("afterEach", "value");
                }
                @Test
                public void test(TestReporter testReporter) {
                    testReporter.publishEntry("test", "value");
                }
            }
        """
        when:
        runTests()

        then:
        testEvents {
            task(":test") {
                root("Gradle Test Run :test") {
                    composite("Gradle Test Executor") {
                        composite("com.example.ReportEntryTest") {
                            test("test(TestReporter)") {
                                metadata("beforeEach", "value")
                                metadata("test", "value")
                                metadata("afterEach", "value")
                            }
                        }
                    }
                }
            }
        }
    }


    def "receives test metadata from JUnit platform tests"() {
        file("src/test/java/com/example/ReportEntryTest.java").java """
            package com.example;

            import org.junit.jupiter.api.AfterAll;
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.TestReporter;
            import org.junit.jupiter.api.extension.MediaType;
            import org.junit.jupiter.api.io.TempDir;

            import java.nio.file.Files;
            import java.nio.file.Path;
            import java.io.IOException;

            public class ReportEntryTest {
                private final Path tempDir;

                ReportEntryTest(TestReporter testReporter, @TempDir Path tempDir) throws IOException {
                    this.tempDir = tempDir;

                    Path constructor = tempDir.resolve("constructor.json");
                    Files.writeString(constructor, "{ constructor: [] }");
                    testReporter.publishFile(constructor, MediaType.APPLICATION_JSON);
                }
                @BeforeEach
                public void beforeEach(TestReporter testReporter) throws IOException {
                    Path beforeEach = tempDir.resolve("beforeEach.json");
                    Files.writeString(beforeEach, "{ beforeEach: [] }");
                    testReporter.publishFile(beforeEach, MediaType.APPLICATION_JSON);
                }
                @AfterEach
                public void afterEach(TestReporter testReporter) throws IOException {
                    Path afterEach = tempDir.resolve("afterEach.json");
                    Files.writeString(afterEach, "{ afterEach: [] }");
                    testReporter.publishFile(afterEach, MediaType.APPLICATION_JSON);
                }
                @Test
                public void test(TestReporter testReporter) throws IOException {
                    Path test = tempDir.resolve("test.json");
                    Files.writeString(test, "{ test: [] }");
                    testReporter.publishFile(test, MediaType.APPLICATION_JSON);
                }
            }
        """
        when:
        runTests()

        then:
        testEvents {
            task(":test") {
                root("Gradle Test Run :test") {
                    composite("Gradle Test Executor") {
                        composite("com.example.ReportEntryTest") {
                            test("test(TestReporter)") {
                                metadata("beforeEach", "value")
                                metadata("test", "value")
                                metadata("afterEach", "value")
                            }
                        }
                    }
                }
            }
        }
    }

    private Object runTests() {
        withConnection {
            ProjectConnection connection ->
                connection.newBuild()
                    .addProgressListener(events, OperationType.TASK, OperationType.TEST, OperationType.TEST_OUTPUT, OperationType.TEST_METADATA)
                    .forTasks('test')
                    .run()
        }
    }
}
