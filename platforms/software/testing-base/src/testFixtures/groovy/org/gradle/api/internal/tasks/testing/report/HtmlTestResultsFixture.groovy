/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report

import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.TextUtil
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HtmlTestResultsFixture {
    Document content

    HtmlTestResultsFixture(TestFile file) {
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

    void assertHasIgnored(int tests) {
        def testDiv = content.select("div#ignored")
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

    void assertHasOverallResult(String result) {
        assert content.select("div#successRate").hasClass(result)
    }

    void assertHasNoSuccessRate() {
        def testDiv = content.select("div#successRate")
        assert testDiv != null
        def counter = testDiv.select("div.percent")
        assert counter != null
        assert counter.text() == "-"
    }

    void assertHasNoFailedTests() {
        def tab = findTab('Failed tests')
        assert tab.isEmpty()
    }

    void assertHasNoIgnoredTests() {
        def tab = findTab('Ignored tests')
        assert tab.isEmpty()
    }

    void assertHasNoNavLinks() {
        assert findTab('Packages').isEmpty()
    }

    void assertHasLinkTo(String target, String display = target) {
        assert content.select("a[href=${target}.html]").find { it.text() == display }
    }

    void assertHasFailedTest(String target, String testName) {
        def tab = findTab('Failed tests')
        assert tab != null
        assert tab.select("a[href=${target}.html#$testName]").find { it.text() == testName }
    }

    void assertHasIgnoredTest(String target, String testName) {
        def tab = findTab('Ignored tests')
        assert tab != null
        assert tab.select("a[href=${target}.html#$testName]").find { it.text() == testName }
    }

    void assertHasTest(String testName) {
        assert findTestDetails(testName)
    }

    HtmlTestResultsFixture.AggregateDetails packageDetails(String packageName) {
        def packageElement = findPackageDetails(packageName)
        assert packageElement != null
        new HtmlTestResultsFixture.AggregateDetails(packageElement)
    }

    HtmlTestResultsFixture.AggregateDetails classDetails(String className) {
        def classElement = findClassDetails(className)
        assert classElement != null
        new HtmlTestResultsFixture.AggregateDetails(classElement)
    }

    HtmlTestResultsFixture.TestDetails testDetails(String testName) {
        def testElement = findTestDetails(testName)
        assert testElement != null
        new HtmlTestResultsFixture.TestDetails(testElement)
    }

    List<HtmlTestResultsFixture.TestDetails> allTestDetails(String testName) {
        def testElements = findAllTestDetails(testName)
        assert testElements != null
        testElements.collect { new HtmlTestResultsFixture.TestDetails(it) }
    }

    void assertHasFailure(String testName, String stackTrace) {
        def detailsRows = findAllTestDetails(testName)
        assert detailsRows.any { it.select("tr > td:eq(2)").text() == 'failed' }
        def tab = findTab('Failed tests')
        assert tab != null && !tab.isEmpty()
        assert tab.select("pre").find { TextUtil.normaliseLineSeparators(it.text()) == stackTrace.trim() }
    }

    private def findTestDetails(String testName) {
        def tab = findTab('Tests')
        def anchor = tab.select("TD").find { it.text() == testName }
        return anchor?.parent()
    }

    private def findAllTestDetails(String testName) {
        def tab = findTab('Tests')
        def anchors = tab.select("TD").findAll { it.text() == testName }
        return anchors.collect { it?.parent() }
    }

    private def findPackageDetails(String packageName) {
        def tab = findTab('Packages')
        def anchor = tab.select("TD").find { it.text() == packageName }
        return anchor?.parent()
    }

    private def findClassDetails(String className) {
        def tab = findTab('Classes')
        def anchor = tab.select("TD").find { it.text() == className }
        return anchor?.parent()
    }

    void assertHasStandardOutput(String stdout) {
        def tab = findTab('Standard output')
        assert tab != null
        assert tab.select("SPAN > PRE").find { it.text() == stdout.trim() }
    }

    void assertHasStandardError(String stderr) {
        def tab = findTab('Standard error')
        assert tab != null
        assert tab.select("SPAN > PRE").find { it.text() == stderr.trim() }
    }

    private def findTab(String title) {
        def tab = content.select("div.tab:has(h2:contains($title))")
        return tab
    }



    class AggregateDetails {
        private final Element tableElement

        AggregateDetails(Element tabElement) {
            this.tableElement = tabElement
        }

        void assertNumberOfTests(int expected) {
            assert tableElement.select("tr > td:eq(1)").text() == "${expected}"
        }

        void assertNumberOfFailures(int expected) {
            assert tableElement.select("tr > td:eq(2)").text() == "${expected}"
        }

        void assertNumberOfIgnored(int expected) {
            assert tableElement.select("tr > td:eq(3)").text() == "${expected}"
        }

        void assertDuration(String expected) {
            assert tableElement.select("tr > td:eq(4)").text() == expected
        }

        void assertSuccessRate(String expected) {
            assert tableElement.select("tr > td:eq(5)").text() == expected
        }

        void assertPassed() {
            assertOverallResult('success')
        }

        void assertIgnored() {
            assertOverallResult('skipped')
        }

        void assertFailed() {
            assertOverallResult('failures')
        }

        private void assertOverallResult(String expected) {
            assert tableElement.select("tr > td:eq(0)").hasClass(expected)
            assert tableElement.select("tr > td:eq(5)").hasClass(expected)
        }

        void assertLinksTo(String target) {
            assert tableElement.select("a[href=${target}.html]") != null
        }
    }

    class TestDetails {
        private final Element tableElement

        TestDetails(Element tabElement) {
            this.tableElement = tabElement
        }

        void assertDuration(String expected) {
            assert tableElement.select("tr > td:eq(1)").text() == expected
        }

        void assertPassed() {
            assertResult('passed', 'success')
        }

        void assertIgnored() {
            assertResult('ignored', 'skipped')
        }

        void assertFailed() {
            assertResult('failed', 'failures')
        }

        private void assertResult(String expectedValue, String expectedClass) {
            assert tableElement.select("tr > td:eq(2)").listIterator().any { Element it -> it.text() == expectedValue }
            assert tableElement.select("tr > td:eq(2)").hasClass(expectedClass)
        }

        boolean failed() {
            return tableElement.select("tr > td:eq(2)").listIterator().any { Element it -> it.text() == 'failed' } &&
              tableElement.select("tr > td:eq(2)").hasClass('failures')
        }
    }
}
