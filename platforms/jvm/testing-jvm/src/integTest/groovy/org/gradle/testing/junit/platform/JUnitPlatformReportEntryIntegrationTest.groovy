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
import org.gradle.api.internal.tasks.testing.report.generic.TestPathRootExecutionResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.JUnitXmlTestExecutionResult

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

        def classLevelMetadata = [constructor: "value1"]
        results.testPath('com.example.ReportEntryTest').onlyRoot().assertMetadata(classLevelMetadata).assertChildrenExecuted("test(TestReporter)")

        def testLevelMetadata = [beforeEach: "value2", test: "value3", afterEach: "value4"]
        results.testPath('com.example.ReportEntryTest:test(TestReporter)').onlyRoot().assertMetadata(testLevelMetadata)

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("com.example.ReportEntryTest")
        clazz.assertMetadata(classLevelMetadata)
        clazz.assertTestMetadata('test(TestReporter)', testLevelMetadata)
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

        def classLevelMetadata = [constructor1: "value1", constructor2: "value2"]
        results.testPath('com.example.ReportEntryTest').onlyRoot().assertMetadata(classLevelMetadata).assertChildrenExecuted("test(TestReporter)")

        def testLevelMetadata = [beforeEach1: "value1", beforeEach2: "value2", test1: "value1", test2: "value2", afterEach1: "value1", afterEach2: "value2"]
        results.testPath('com.example.ReportEntryTest:test(TestReporter)').onlyRoot().assertMetadata(testLevelMetadata)

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("com.example.ReportEntryTest")
        clazz.assertMetadata(classLevelMetadata)
        clazz.assertTestMetadata('test(TestReporter)', testLevelMetadata)
    }

    def "captures file entry emitted by tests"() {
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
                    Path beforeEach = tempDir.resolve("beforeEach.svg");
                    Files.writeString(beforeEach, "<svg xmlns=\\"http://www.w3.org/2000/svg\\" viewBox=\\"0 0 800 600\\"><rect width=\\"800\\" height=\\"600\\" fill=\\"#F5F5F0\\"/><rect width=\\"350\\" height=\\"600\\" fill=\\"#D4292E\\"/><rect x=\\"350\\" width=\\"450\\" height=\\"250\\" fill=\\"#1356A3\\"/><rect x=\\"350\\" y=\\"250\\" width=\\"200\\" height=\\"350\\" fill=\\"#F7D838\\"/><rect x=\\"550\\" y=\\"250\\" width=\\"250\\" height=\\"350\\" fill=\\"#F5F5F0\\"/><line x1=\\"350\\" y1=\\"0\\" x2=\\"350\\" y2=\\"600\\" stroke=\\"#1A1A1A\\" stroke-width=\\"12\\"/><line x1=\\"350\\" y1=\\"250\\" x2=\\"800\\" y2=\\"250\\" stroke=\\"#1A1A1A\\" stroke-width=\\"12\\"/><line x1=\\"550\\" y1=\\"250\\" x2=\\"550\\" y2=\\"600\\" stroke=\\"#1A1A1A\\" stroke-width=\\"12\\"/><rect width=\\"800\\" height=\\"600\\" fill=\\"none\\" stroke=\\"#1A1A1A\\" stroke-width=\\"14\\"/></svg>");
                    testReporter.publishFile(beforeEach, MediaType.create("image", "svg+xml"));
                }
                @AfterEach
                public void afterEach(TestReporter testReporter) throws IOException {
                    Path afterEach = tempDir.resolve("afterEach.mp4");
                    Files.writeString(afterEach, "{ afterEach: [] }");
                    testReporter.publishFile(afterEach, MediaType.create("video", "mp4"));
                }
                @Test
                public void test(TestReporter testReporter) throws IOException {
                    Path test = tempDir.resolve("test.txt");
                    Files.writeString(test, "hello world");
                    testReporter.publishFile(test, MediaType.TEXT_PLAIN);
                }
            }
        """
        when:
        succeeds("test")
        then:
        def results = resultsFor(testDirectory)

        def constructorFile = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/constructor.json").assertExists()
        def beforeEachFile = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/beforeEach.svg").assertExists()
        def testFile = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/test.txt").assertExists()
        def afterEachFile = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/afterEach.mp4").assertExists()

        results.testPath('com.example.ReportEntryTest').onlyRoot()
            .assertFileAttachments([ (constructorFile.name): TestPathRootExecutionResult.ShowAs.LINK ])
            .assertChildrenExecuted("test(TestReporter)")

        results.testPath('com.example.ReportEntryTest:test(TestReporter)').onlyRoot()
            .assertFileAttachments([
                (beforeEachFile.name): TestPathRootExecutionResult.ShowAs.IMAGE,
                (testFile.name): TestPathRootExecutionResult.ShowAs.LINK,
                (afterEachFile.name): TestPathRootExecutionResult.ShowAs.VIDEO
            ])

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("com.example.ReportEntryTest")
        clazz.assertHasFileAttachments(constructorFile)
        clazz.assertTestHasFileAttachments('test(TestReporter)', beforeEachFile, testFile, afterEachFile)
    }

    def "captures dir entry emitted by tests"() {
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
                    Path dir = tempDir.resolve("constructor");
                    Files.createDirectory(dir);
                    Path constructor = dir.resolve("constructor.json");
                    Files.writeString(constructor, "{ constructor: [] }");
                    testReporter.publishDirectory(dir);
                }
                @BeforeEach
                public void beforeEach(TestReporter testReporter) throws IOException {
                    Path dir = tempDir.resolve("beforeEach");
                    Files.createDirectory(dir);
                    Path beforeEach = dir.resolve("beforeEach.json");
                    Files.writeString(beforeEach, "{ beforeEach: [] }");
                    testReporter.publishDirectory(dir);
                }
                @AfterEach
                public void afterEach(TestReporter testReporter) throws IOException {
                    Path dir = tempDir.resolve("afterEach");
                    Files.createDirectory(dir);
                    Path afterEach = dir.resolve("afterEach.json");
                    Files.writeString(afterEach, "{ afterEach: [] }");
                    testReporter.publishDirectory(dir);
                }
                @Test
                public void test(TestReporter testReporter) throws IOException {
                    Path dir = tempDir.resolve("test");
                    Files.createDirectory(dir);
                    Path test = dir.resolve("test.json");
                    Files.writeString(test, "{ test: [] }");
                    testReporter.publishDirectory(dir);
                }
            }
        """
        when:
        succeeds("test")
        then:
        def results = resultsFor(testDirectory)

        def constructorDir = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/constructor").assertIsDir()
        def beforeEachDir = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/beforeEach").assertIsDir()
        def testDir = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/test").assertIsDir()
        def afterEachDir = file("build/junit-jupiter/com.example.ReportEntryTest/test(org.junit.jupiter.api.TestReporter)/afterEach").assertIsDir()

        results.testPath('com.example.ReportEntryTest').onlyRoot()
            .assertFileAttachments([(constructorDir.name): TestPathRootExecutionResult.ShowAs.LINK])
            .assertChildrenExecuted("test(TestReporter)")
        results.testPath('com.example.ReportEntryTest:test(TestReporter)').onlyRoot().assertFileAttachments([
            (beforeEachDir.name): TestPathRootExecutionResult.ShowAs.LINK,
            (testDir.name): TestPathRootExecutionResult.ShowAs.LINK,
            (afterEachDir.name): TestPathRootExecutionResult.ShowAs.LINK
            ])

        def xmlReport = new JUnitXmlTestExecutionResult(testDirectory)
        def clazz = xmlReport.testClass("com.example.ReportEntryTest")
        clazz.assertHasFileAttachments(constructorDir)
        clazz.assertTestHasFileAttachments('test(TestReporter)', beforeEachDir, testDir, afterEachDir)
    }

    @Override
    GenericTestExecutionResult.TestFramework getTestFramework() {
        return GenericTestExecutionResult.TestFramework.JUNIT_JUPITER
    }
}
