/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report

import org.gradle.api.Action
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider
import org.gradle.api.internal.tasks.testing.logging.SimpleTestResult
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestResult
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ConfigureUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Rule
import spock.lang.Specification

class DefaultTestReportTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final DefaultTestReport report = new DefaultTestReport()
    final TestFile reportDir = tmpDir.file('report')
    final TestFile indexFile = reportDir.file('index.html')
    final TestResultsProvider testResultProvider = Mock()

    def generatesReportWhenThereAreNoTestResults() {
        given:
        emptyResultSet()

        when:
        report.generateReport(testResultProvider, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(0)
        index.assertHasFailures(0)
        index.assertHasNoDuration()
        index.assertHasNoSuccessRate()
        index.assertHasNoNavLinks()
    }

    TestResultsProvider buildResults(Closure closure) {
        TestResultsBuilder builder = new TestResultsBuilder()
        ConfigureUtil.configure(closure, builder)
        return builder;
    }

    def generatesReportWhichIncludesContentsOfEachTestResultFile() {
        given:
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase("test1") {
                    duration = 1
                }
                testcase("test2") {
                    duration = 4
                }
                stdout = "this is\nstandard output"
                stderr = "this is\nstandard error"
            }
            testClassResult("org.gradle.Test2") {
                testcase("test3") {
                    duration = 102001
                }
            }
            testClassResult("org.gradle.sub.Test") {
                testcase("test4") {
                    duration = 12900
                }
            }
        }

        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(4)
        index.assertHasFailures(0)
        index.assertHasSuccessRate(100)
        index.assertHasDuration("1m54.91s")
        index.assertHasLinkTo('org.gradle')
        index.assertHasLinkTo('org.gradle.sub')
        index.assertHasLinkTo('org.gradle.Test', 'org.gradle.Test')

        reportDir.file("style.css").assertIsFile()

        def packageFile = results(reportDir.file('org.gradle.html'))
        packageFile.assertHasTests(3)
        packageFile.assertHasFailures(0)
        packageFile.assertHasSuccessRate(100)
        packageFile.assertHasDuration("1m42.01s")
        packageFile.assertHasLinkTo('org.gradle.Test', 'Test')
        packageFile.assertHasLinkTo('org.gradle.Test2', 'Test2')

        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTests(2)
        testClassFile.assertHasFailures(0)
        testClassFile.assertHasSuccessRate(100)
        testClassFile.assertHasDuration("0.005s")
        testClassFile.assertHasTest('test1')
        testClassFile.assertHasTest('test2')
        testClassFile.assertHasStandardOutput('this is\nstandard output')
        testClassFile.assertHasStandardError('this is\nstandard error')
    }

    def generatesReportWhenThereAreFailures() {
        given:
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase("test1") {
                    duration = 0
                    failure("something failed", "this is the failure\nat someClass")
                }
                testcase("test2") {
                    duration = 0
                    failure("a multi-line\nmessage\"", "this is a failure.")
                }
                stdout = "this is\nstandard output"
                stderr = "this is\nstandard error"
            }

            testClassResult("org.gradle.Test2") {
                testcase("test1") {
                    duration = 0
                }
            }
            testClassResult("org.gradle.sub.Test") {
                testcase("test1") {
                    duration = 0
                }
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(4)
        index.assertHasFailures(2)
        index.assertHasSuccessRate(50)
        index.assertHasFailedTest('org.gradle.Test', 'test1')

        def packageFile = results(reportDir.file('org.gradle.html'))
        packageFile.assertHasTests(3)
        packageFile.assertHasFailures(2)
        packageFile.assertHasSuccessRate(33)
        packageFile.assertHasFailedTest('org.gradle.Test', 'test1')

        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTests(2)
        testClassFile.assertHasFailures(2)
        testClassFile.assertHasSuccessRate(0)
        testClassFile.assertHasTest('test1')
        testClassFile.assertHasFailure('test1', 'this is the failure\nat someClass\n')
        testClassFile.assertHasTest('test2')
        testClassFile.assertHasFailure('test2', 'this is a failure.')
    }

    def generatesReportWhenThereAreIgnoredTests() {
        given:
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase("test1") {
                    resultType = TestResult.ResultType.SKIPPED
                }
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasTests(1)
        index.assertHasFailures(0)
        index.assertHasSuccessRate(100)

        def packageFile = results(reportDir.file('org.gradle.html'))
        packageFile.assertHasTests(1)
        packageFile.assertHasFailures(0)
        packageFile.assertHasSuccessRate(100)

        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTests(1)
        testClassFile.assertHasFailures(0)
        testClassFile.assertHasSuccessRate(100)
        testClassFile.assertHasTest('test1')
        testClassFile.assertTestIgnored('test1')
    }

    def reportsOnClassesInDefaultPackage() {
        given:
        def testTestResults = buildResults {
            testClassResult("Test") {
                testcase("test1") {
                    duration = 0
                }
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def index = results(indexFile)
        index.assertHasLinkTo('default-package')
        index.assertHasLinkTo('Test')

        def packageFile = results(reportDir.file('default-package.html'))
        packageFile.assertHasLinkTo('Test')
    }

    def escapesHtmlContentInReport() {
        given:
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase("test1 < test2") {
                    duration = 0
                    failure("something failed", "<a failure>")
                }
                stdout = "</html> & "
                stderr = "</div> & "
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTest('test1 < test2')
        testClassFile.assertHasFailure('test1 < test2', '<a failure>')
        testClassFile.assertHasStandardOutput('</html> & ')
        testClassFile.assertHasStandardError('</div> & ')
    }

    def encodesUnicodeCharactersInReport() {
        given:
        def testTestResults = buildResults {
            testClassResult("org.gradle.Test") {
                testcase('\u0107') {
                    duration = 0
                }
                stdout = "out:\u0256"
                stderr = "err:\u0102"
            }
        }
        when:
        report.generateReport(testTestResults, reportDir)

        then:
        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTest('\u0107')
        testClassFile.assertHasStandardOutput('out:\u0256')
        testClassFile.assertHasStandardError('err:\u0102')
    }

    def results(TestFile file) {
        return new TestResultsFixture(file)
    }

    def emptyResultSet() {
        _ * testResultProvider.visitClasses(_)
    }
}

class TestResultsBuilder implements TestResultsProvider {
    def testClasses = [:]

    void testClassResult(String className, Closure configClosure) {
        BuildableTestClassResult testSuite = new BuildableTestClassResult(className, System.currentTimeMillis())
        ConfigureUtil.configure(configClosure, testSuite)

        testClasses[className] = testSuite
    }

    void writeOutputs(String className, TestOutputEvent.Destination destination, Writer writer) {
        if (destination == TestOutputEvent.Destination.StdOut) {
            writer.append(testClasses[className].stdout);
        } else if (destination == TestOutputEvent.Destination.StdErr) {
            writer.append(testClasses[className].stderr);
        }
    }

    void visitClasses(Action<? super TestClassResult> visitor) {
        testClasses.values().each {
            visitor.execute(it)
        }
    }

    boolean hasOutput(String className, TestOutputEvent.Destination destination) {
        if (destination == TestOutputEvent.Destination.StdOut) {
            return testClasses[className].stdout != null && testClasses[className].stdout.length() != 0
        } else if (destination == TestOutputEvent.Destination.StdErr) {
            return testClasses[className].stderr != null && testClasses[className].stderr.length() != 0
        }
    }

    private static class BuildableTestClassResult extends TestClassResult {
        String stderr;
        String stdout;

        BuildableTestClassResult(String className, long startTime) {
            super(className, startTime)
        }

        TestMethodResult testcase(String name) {
            BuildableTestMethodResult methodResult = new BuildableTestMethodResult(name, new SimpleTestResult())
            add(methodResult)
            return methodResult
        }

        def testcase(String name, Closure configClosure) {
            BuildableTestMethodResult methodResult = testcase(name);
            ConfigureUtil.configure(configClosure, methodResult)
            return methodResult
        }
    }

    private static class BuildableTestMethodResult extends TestMethodResult {
        long duration
        List<Throwable> exceptions = []
        TestResult.ResultType resultType = TestResult.ResultType.SUCCESS

        BuildableTestMethodResult(String name, TestResult result) {
            super(name, result)
            duration = result.endTime - result.startTime;
        }

        void failure(String message, String text) {
            exceptions.add(new ResultException(message, text));
        }
    }

    private static class ResultException extends Exception {
        private final String message
        private final String text

        public ResultException(String message, String text) {
            this.text = text
            this.message = message
        }

        public String toString() {
            return message
        }

        public void printStackTrace(PrintWriter s) {
            s.print(text);
        }
    }
}

class TestResultsFixture {
    Document content

    TestResultsFixture(TestFile file) {
        file.assertIsFile()
        content = Jsoup.parse(file, null)
    }

    void assertHasTests(int tests) {
        def testDiv = content.select("div#tests")
        assert testDiv != null
        def counter = testDiv.select("div.counter")
        assert counter != null
        assert counter.text() == tests as String
    }

    void assertHasFailures(int tests) {
        def testDiv = content.select("div#failures")
        assert testDiv != null
        def counter = testDiv.select("div.counter")
        assert counter != null
        assert counter.text() == tests as String
    }

    void assertHasDuration(String duration) {
        def testDiv = content.select("div#duration")
        assert testDiv != null
        def counter = testDiv.select("div.counter")
        assert counter != null
        assert counter.text() == duration as String

    }

    void assertHasNoDuration() {
        def testDiv = content.select("div#duration")
        assert testDiv != null
        def counter = testDiv.select("div.counter")
        assert counter != null
        assert counter.text() == "-"
    }

    void assertHasSuccessRate(int rate) {
        def testDiv = content.select("div#successRate")
        assert testDiv != null
        def counter = testDiv.select("div.percent")
        assert counter != null
        assert counter.text() == "${rate}%"
    }

    void assertHasNoSuccessRate() {
        def testDiv = content.select("div#successRate")
        assert testDiv != null
        def counter = testDiv.select("div.percent")
        assert counter != null
        assert counter.text() == "-"
    }

    void assertHasNoNavLinks() {
        assert findTab('Packages').isEmpty()
    }

    void assertHasLinkTo(String target, String display = target) {
        assert content.select("a[href=${target}.html]").find{it.text() == display}
    }

    void assertHasFailedTest(String className, String testName) {
        def tab = findTab('Failed tests')
        assert tab != null
        assert tab.select("a[href=${className}.html#$testName]").find{it.text() == testName}
    }

    void assertHasTest(String testName) {
        assert findTestDetails(testName)
    }

    void assertTestIgnored(String testName) {
        def row = findTestDetails(testName)
        assert row.select("tr > td:eq(2)").text() == 'ignored'
    }

    void assertHasFailure(String testName, String stackTrace) {
        def detailsRow = findTestDetails(testName)
        assert detailsRow.select("tr > td:eq(2)").text() == 'failed'
        def tab = findTab('Failed tests')
        assert tab != null && !tab.isEmpty()
        assert tab.select("pre").find {it.text() == stackTrace.trim() }
    }

    private def findTestDetails(String testName) {
        def tab = findTab('Tests')
        def anchor = tab.select("TD").find {it.text() == testName}
        return anchor?.parent()
    }

    void assertHasStandardOutput(String stdout) {
        def tab = findTab('Standard output')
        assert tab != null
        assert tab.select("SPAN > PRE").find{it.text() == stdout.trim() }
    }

    void assertHasStandardError(String stderr) {
        def tab = findTab('Standard error')
        assert tab != null
        assert tab.select("SPAN > PRE").find{it.text() == stderr.trim() }
    }

    private def findTab(String title) {
        def tab = content.select("div.tab:has(h2:contains($title))")
        return tab
    }
}
