/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.testing

import org.gradle.api.internal.tasks.testing.report.AggregateTestReportFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires(TestExecutionPreconditions.NotEmbeddedExecutor)
class AggregateTestReportIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            include("app", "lib")
        """
        ["app", "lib"].each { project ->
            file("$project/build.gradle") << """
                plugins {
                    id("java-library")
                }
                ${mavenCentralRepository()}
                testing.suites.test.useJUnitJupiter()
            """
        }
        file("lib/src/test/java/lib/LibTest.java") << """
            package lib;
            import org.junit.jupiter.api.*;
            public class LibTest {
                @Test void passes() {}
                @Test @Disabled void skipped() {}
            }
        """
    }

    def "summarizes and links to the reports of all executed test tasks"() {
        given:
        file("app/src/test/java/app/AppTest.java") << """
            package app;
            import org.junit.jupiter.api.*;
            public class AppTest {
                @Test void passes() {}
                @Test void fails() { throw new AssertionError("boom"); }
            }
        """

        when:
        fails("test", "--continue")

        then:
        // Only one test task failed, so its own report is the one to look at
        outputDoesNotContain("Aggregate test results")
        def report = AggregateTestReportFixture.of(testDirectory)
        report.totals == [tests: "4", failures: "1", skipped: "1", successful: "75%"]
        report.rows*.name() == ["Gradle Test Run :app:test", "Gradle Test Run :lib:test"]

        def app = report.row("Gradle Test Run :app:test")
        app.href() == "../../../app/build/reports/tests/test/index.html"
        [app.result(), app.tests(), app.passed(), app.failed(), app.skipped()] == ["failed", 2, 1, 1, 0]
        report.failedRows*.name() == ["Gradle Test Run :app:test"]
        report.linkedReport("Gradle Test Run :app:test") == file("app/build/reports/tests/test/index.html")

        def lib = report.row("Gradle Test Run :lib:test")
        lib.href() == "../../../lib/build/reports/tests/test/index.html"
        [lib.result(), lib.tests(), lib.passed(), lib.failed(), lib.skipped()] == ["skipped", 2, 1, 0, 1]
    }

    def "does not print the aggregate report if all tests pass"() {
        when:
        succeeds(":lib:test")

        then:
        outputDoesNotContain("Aggregate test results")
        AggregateTestReportFixture.of(testDirectory).rows*.name() == ["Gradle Test Run :lib:test"]
    }

    def "summarizes test tasks without an HTML report without linking to it"() {
        given:
        file("lib/build.gradle") << """
            tasks.test.reports.html.required = false
        """

        when:
        succeeds(":lib:test")

        then:
        def lib = AggregateTestReportFixture.of(testDirectory).row("Gradle Test Run :lib:test")
        lib.href() == null
        [lib.result(), lib.tests(), lib.passed(), lib.failed(), lib.skipped()] == ["skipped", 2, 1, 0, 1]
    }

    def "only summarizes test tasks that executed"() {
        when:
        succeeds(":lib:test")

        then:
        AggregateTestReportFixture.of(testDirectory).indexFile.assertIsFile()

        when:
        succeeds(":lib:test")

        then:
        skipped(":lib:test")
        AggregateTestReportFixture.of(testDirectory).indexFile.assertDoesNotExist()
    }

}
