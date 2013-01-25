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
package org.gradle.integtests.fixtures

import org.hamcrest.Matcher
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

import static junit.framework.Assert.assertTrue
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertThat

class HtmlTestExecutionResult implements TestExecutionResult {

    private File htmlReportDirectory

    public HtmlTestExecutionResult(File projectDirectory, String testReportDirectory = "tests") {
        this.htmlReportDirectory = new File(projectDirectory, "build/reports/$testReportDirectory");
    }

    TestExecutionResult assertTestClassesExecuted(String... testClasses) {
        indexContainsTestClass(testClasses)
        assertHtmlReportForTestClassExists(testClasses)
        return this
    }

    def indexContainsTestClass(String... testClasses) {
        def indexFile = new File(htmlReportDirectory, "index.html")
        assert indexFile.exists()
        Document html = Jsoup.parse(indexFile, "utf-8")
        testClasses.each { testClass ->
            assert html.select("a").find { it.text() == testClass } != null
        }
    }

    def assertHtmlReportForTestClassExists(String... classNames) {
        classNames.each {
            assertTrue new File(htmlReportDirectory, "${it}.html").exists();
        }
    }

    TestClassExecutionResult testClass(String testClass) {
        return new HtmlTestClassExecutionResult(new File(htmlReportDirectory, "${testClass}.html"));
    }

    private static class HtmlTestClassExecutionResult implements TestClassExecutionResult {
        private File htmlFile
        private List testsExecuted = []
        private List testsSucceeded = []
        private Map testsFailures = [:]
        private Set testsSkipped = []
        private Document html

        public HtmlTestClassExecutionResult(File htmlFile) {
            this.htmlFile = htmlFile;
            this.html = Jsoup.parse(htmlFile, "utf-8")
            parseTestClassFile()
        }

        def parseTestClassFile() {
            html.select("tr > td.success:eq(0)" ).each {
                testsExecuted << it.text()
                testsSucceeded << it.text()

            }
            html.select("tr > td.failures:eq(0)").each {
                testsExecuted << it.text()
                String failure = getFailureMessage(it.text());
                testsFailures[it.text()] = failure
            }

            html.select("td.skipped").each {
                testsSkipped << it.text()
                testsExecuted << it.text()
            }
            return this
        }

        String getFailureMessage(String testmethod) {
            html.select("div#test:has(a[name=$testmethod]) > span > pre").text()
        }

        TestClassExecutionResult assertTestsExecuted(String... testNames) {
            assertThat(testsExecuted, equalTo(testNames as List))
            return this
        }

        TestClassExecutionResult assertTestCount(int tests, int failures, int errors) {
            assert tests == testsExecuted.size()
            assert failures == testsFailures.size()
            return this
        }

        TestClassExecutionResult assertTestsSkipped(String... testNames) {
            assertThat(testsSkipped, equalTo(testNames as Set))
            return this
        }

        TestClassExecutionResult assertTestPassed(String name) {
            assert testsSucceeded.contains(name);
            return this
        }

        TestClassExecutionResult assertTestFailed(String name, Matcher<? super String>... messageMatchers) {
            def message = testsFailures[name];
            messageMatchers.each {it.matches(message)}
            return this
        }

        TestClassExecutionResult assertTestSkipped(String name) {
            assert testsSkipped.contains(name);
            return this
        }

        TestClassExecutionResult assertConfigMethodPassed(String name) {
            return null
        }

        TestClassExecutionResult assertConfigMethodFailed(String name) {
            return null
        }

        TestClassExecutionResult assertStdout(Matcher<? super String> matcher) {
            matcher.matches(html.select("div#tab2 > span > pre").text())
            return this;
        }

        TestClassExecutionResult assertStderr(Matcher<? super String> matcher) {
            matcher.matches(html.select("div#tab3 > span > pre").text())
            return this;
        }
    }
}
