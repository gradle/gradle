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

import org.apache.commons.lang.StringEscapeUtils
import org.gradle.util.TemporaryFolder
import org.gradle.util.TestFile
import org.junit.Rule
import spock.lang.Specification

class DefaultTestReportTest extends Specification {
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    final DefaultTestReport report = new DefaultTestReport()
    final TestFile reportDir = tmpDir.file('report')
    final TestFile resultsDir = tmpDir.file('results')
    final TestFile indexFile = reportDir.file('index.html')

    def setup() {
        report.testReportDir = reportDir
        report.testResultsDir = resultsDir
    }

    def generatesReportWhenResultsDirectoryDoesNotExist() {
        when:
        report.generateReport()

        then:
        def index = results(indexFile)
        index.assertHasTests(0)
    }

    def generatesReportWhenThereAreNoTestResults() {
        resultsDir.mkdir()

        when:
        report.generateReport()

        then:
        def index = results(indexFile)
        index.assertHasTests(0)
        index.assertHasFailures(0)
        index.assertHasNoDuration()
        index.assertHasNoSuccessRate()
        index.assertHasNoNavLinks()
    }

    def generatesReportWhichIncludesContentsOfEachTestResultFile() {
        resultsDir.file('TEST-someClass.xml') << '''
<testsuite name="org.gradle.Test">
    <testcase classname="org.gradle.Test" name="test1" time="0.0010"/>
    <testcase classname="org.gradle.Test" name="test2" time="0.0040"/>
    <system-out>this is
standard output</system-out>
    <system-err>this is
standard error</system-err>
</testsuite>
'''
        resultsDir.file('TEST-someOtherClass.xml') << '''
<testsuite name="org.gradle.Test2">
    <testcase classname="org.gradle.Test2" name="test1" time="102.0010"/>
    <testcase classname="org.gradle.sub.Test" name="test1" time="12.9"/>
</testsuite>
'''

        when:
        report.generateReport()

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
        resultsDir.file('TEST-someClass.xml') << '''
<testsuite>
    <testcase classname="org.gradle.Test" name="test1" time="0"><failure message="something failed">this is the failure
at someClass
</failure></testcase>
    <testcase classname="org.gradle.Test" name="test2" time="0"><failure message="a multi-line
message">this is a failure.</failure></testcase>
    <testcase classname="org.gradle.Test2" name="test1" time="0"/>
    <testcase classname="org.gradle.sub.Test" name="test1" time="0"/>
</testsuite>
'''

        when:
        report.generateReport()

        then:
        def index = results(indexFile)
        index.assertHasTests(4)
        index.assertHasFailures(2)
        index.assertHasSuccessRate(50)
        index.assertHasLinkToTest('org.gradle.Test', 'test1')

        def packageFile = results(reportDir.file('org.gradle.html'))
        packageFile.assertHasTests(3)
        packageFile.assertHasFailures(2)
        packageFile.assertHasSuccessRate(33)
        packageFile.assertHasLinkToTest('org.gradle.Test', 'test1')

        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTests(2)
        testClassFile.assertHasFailures(2)
        testClassFile.assertHasSuccessRate(0)
        testClassFile.assertHasTest('test1')
        testClassFile.assertHasFailure('test1', 'this is the failure\nat someClass\n')
        testClassFile.assertHasTest('test2')
        testClassFile.assertHasFailure('test2', 'this is a failure.')
    }

    def reportsOnClassesInDefaultPackage() {
        resultsDir.file('TEST-someClass.xml') << '''
<testsuite name="Test">
    <testcase classname="Test" name="test1" time="0">
    </testcase>
</testsuite>
'''

        when:
        report.generateReport()

        then:
        def index = results(indexFile)
        index.assertHasLinkTo('default-package')
        index.assertHasLinkTo('Test')

        def packageFile = results(reportDir.file('default-package.html'))
        packageFile.assertHasLinkTo('Test')
    }

    def escapesHtmlContentInReport() {
        resultsDir.file('TEST-someClass.xml') << '''
<testsuite name="org.gradle.Test">
    <testcase classname="org.gradle.Test" name="test1 &lt; test2" time="0">
        <failure message="something failed">&lt;a failure&gt;</failure>
    </testcase>
    <system-out>&lt;/html> &amp; </system-out>
    <system-err>&lt;/div> &amp; </system-err>

</testsuite>
'''

        when:
        report.generateReport()

        then:
        def testClassFile = results(reportDir.file('org.gradle.Test.html'))
        testClassFile.assertHasTest('test1 < test2')
        testClassFile.assertHasFailure('test1 < test2', '<a failure>')
        testClassFile.assertHasStandardOutput('</html> & ')
        testClassFile.assertHasStandardError('</div> & ')
    }

    def ignoresFilesWhichAreNotResultFiles() {
        resultsDir.file('TEST-someClass.xml') << '''
<testsuite name="org.gradle.Test">
    <testcase classname="org.gradle.Test" name="test1" time="0"></testcase>
</testsuite>
'''
        resultsDir.file('TESTS-broken.xml') << 'broken'

        when:
        report.generateReport()

        then:
        results(indexFile).assertHasTests(1)
    }

    def results(TestFile file) {
        return new TestResultsFixture(file)
    }
}

class TestResultsFixture {
    final TestFile file
    final String text

    TestResultsFixture(TestFile file) {
        this.file = file
        file.assertIsFile()
        text = file.text
    }

    void assertHasTests(int tests) {
        assert text.contains("<div class='counter'>${tests}</div>\n<p>tests</p>")
    }

    void assertHasFailures(int tests) {
        assert text.contains("<div class='counter'>${tests}</div>\n<p>failures</p>")
    }

    void assertHasDuration(String duration) {
        assert text.contains("<div class='counter'>${duration}</div>\n<p>duration</p>")
    }

    void assertHasNoDuration() {
        assert text.contains("<div class='counter'>-</div>\n<p>duration</p>")
    }
    
    void assertHasSuccessRate(int rate) {
        assert text.contains("<div class='percent'>${rate}%</div>\n<p>successful</p>")
    }

    void assertHasNoSuccessRate() {
        assert text.contains("<div class='percent'>-</div>\n<p>successful</p>")
    }

    void assertHasNoNavLinks() {
        assert !text.contains("Packages")
    }

    void assertHasLinkTo(String target, String display = target) {
        assert text.contains("<a href='${target}.html'>${StringEscapeUtils.escapeHtml(display)}</a>")
    }

    void assertHasLinkToTest(String className, String testName) {
        String escapedName = StringEscapeUtils.escapeHtml(testName)
        assert text.contains("<a href='${className}.html#${escapedName}'>${escapedName}</a>")
    }

    void assertHasTest(String testName) {
        assert text.contains("<a name='${StringEscapeUtils.escapeHtml(testName)}'> </a>")
    }

    void assertHasFailure(String testName, String stackTrace) {
        assert text.contains("<pre class='stackTrace'>${StringEscapeUtils.escapeHtml(stackTrace)}</pre>")
    }

    void assertHasStandardOutput(String stdout) {
        assert text.contains("<h2>Standard output</h2>\n<pre>${StringEscapeUtils.escapeHtml(stdout)}</pre>")
    }
    
    void assertHasStandardError(String stderr) {
        assert text.contains("<h2>Standard error</h2>\n<pre>${StringEscapeUtils.escapeHtml(stderr)}</pre>")
    }
}
