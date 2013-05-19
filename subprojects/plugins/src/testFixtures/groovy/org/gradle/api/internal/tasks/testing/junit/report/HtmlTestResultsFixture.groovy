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

package org.gradle.api.internal.tasks.testing.junit.report

import org.gradle.test.fixtures.file.TestFile
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
        assert content.select("a[href=${target}.html]").find { it.text() == display }
    }

    void assertHasFailedTest(String className, String testName) {
        def tab = findTab('Failed tests')
        assert tab != null
        assert tab.select("a[href=${className}.html#$testName]").find { it.text() == testName }
    }

    void assertHasTest(String testName) {
        assert findTestDetails(testName)
    }

    HtmlTestResultsFixture.PackageDetails packageDetails(String packageName) {
        def packageElement = findPackageDetails(packageName)
        new HtmlTestResultsFixture.PackageDetails(packageElement)
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
        assert tab.select("pre").find { it.text() == stackTrace.trim() }
    }

    private def findTestDetails(String testName) {
        def tab = findTab('Tests')
        def anchor = tab.select("TD").find { it.text() == testName }
        return anchor?.parent()
    }

    private def findPackageDetails(String packageName) {
        def tab = findTab('Packages')
        def anchor = tab.select("TD").find { it.text() == packageName }
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

    class PackageDetails {
        private final Element packageElement

        PackageDetails(Element packageElement) {
            this.packageElement = packageElement
        }

        void assertSuccessRate(int expected) {
            assert packageElement.select("tr > td:eq(4)").text() == "${expected}%"
        }
    }
}
