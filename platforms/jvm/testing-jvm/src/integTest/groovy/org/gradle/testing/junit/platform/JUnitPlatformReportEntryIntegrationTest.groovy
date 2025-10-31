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

package org.gradle.testing.junit.platform

import org.gradle.api.internal.tasks.testing.report.VerifiesGenericTestReportResults
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JUnitPlatformReportEntryIntegrationTest extends AbstractIntegrationSpec implements VerifiesGenericTestReportResults {
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

    def "captures simple report entry emitted by tests"() {
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
                    testReporter.publishEntry("constructor", "value1");
                }

                @BeforeEach
                public void beforeEach(TestReporter testReporter) {
                    testReporter.publishEntry("beforeEach", "value2");
                }
                @Test
                public void test(TestReporter testReporter) {
                    testReporter.publishEntry("test", "value3");
                }
                @AfterEach
                public void afterEach(TestReporter testReporter) {
                    testReporter.publishEntry("afterEach", "value4");
                }
            }
        """
        when:
        succeeds("test")
        then:
        def results = resultsFor(testDirectory)
        results.testPath('com.example.ReportEntryTest').onlyRoot().assertMetadata([constructor: "value1"]).assertChildrenExecuted("test(TestReporter)")
        results.testPath('com.example.ReportEntryTest:test(TestReporter)').onlyRoot().assertMetadata([beforeEach: "value2", test: "value3", afterEach: "value4"])
    }

    def "captures map report entry emitted by tests"() {
        file("src/test/java/com/example/ReportEntryTest.java").java """
            package com.example;

            import org.junit.jupiter.api.AfterAll;
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeAll;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.api.TestReporter;

            import java.util.Map;
            import java.util.LinkedHashMap;

            public class ReportEntryTest {
                private Map<String, String> of(String k1, String v1, String k2, String v2) {
                    final Map<String, String> map = new LinkedHashMap<>();
                    map.put(k1, v1);
                    map.put(k2, v2);
                    return map;
                }
                ReportEntryTest(TestReporter testReporter) {
                    testReporter.publishEntry(of("constructor1", "value1", "constructor2", "value2"));
                }
                @BeforeEach
                public void beforeEach(TestReporter testReporter) {
                    testReporter.publishEntry(of("beforeEach1", "value1", "beforeEach2", "value2"));
                }
                @Test
                public void test(TestReporter testReporter) {
                    testReporter.publishEntry(of("test1", "value1", "test2", "value2"));
                }
                @AfterEach
                public void afterEach(TestReporter testReporter) {
                    testReporter.publishEntry(of("afterEach1", "value1", "afterEach2", "value2"));
                }
            }
        """
        when:
        succeeds("test")
        then:
        def results = resultsFor(testDirectory)
        results.testPath('com.example.ReportEntryTest').onlyRoot().assertMetadata([constructor1: "value1", constructor2: "value2"]).assertChildrenExecuted("test(TestReporter)")
        results.testPath('com.example.ReportEntryTest:test(TestReporter)').onlyRoot().assertMetadata([beforeEach1: "value1", beforeEach2: "value2", test1: "value1", test2: "value2", afterEach1: "value1", afterEach2: "value2"])
    }

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT_JUPITER
    }
}
