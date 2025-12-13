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

package org.gradle.integtests.fixtures.configurationcache

import groovy.json.JsonSlurper
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.Matcher

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

/**
 * A fixture to perform assertions on the contents of the Configuration Cache Report.
 */
abstract class ConfigurationCacheReportFixture {
    protected ConfigurationCacheReportFixture() {}

    /**
     * Creates a fixture for the absent report. The provided project root is used for error messages only
     *
     * @param projectRoot the root directory for the build in which the report was expected to be
     * @return the fixture
     */
    static ConfigurationCacheReportFixture forAbsentReport(File projectRoot) {
        return new NoReportFixture(projectRoot)
    }

    /**
     * Creates a fixture for an existing report. This method also asserts that the report exists and is readable.
     *
     * @param reportFile the HTML report file
     * @return the fixture
     */
    static ConfigurationCacheReportFixture forReportFile(File reportFile) {
        return new ExistingReportFixture(reportFile)
    }

    /**
     * Asserts that the report has specified problems, inputs, and incompatible tasks.
     *
     * @param specClosure the content assertions
     */
    void assertContents(@DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure) {
        HasConfigurationCacheProblemsSpec spec = ConfigurationCacheProblemsFixture.newProblemsSpec(ConfigureUtil.configureUsing(specClosure))
        spec.checkReportProblems = true
        assertContents(spec)
    }

    protected abstract void assertContents(HasConfigurationCacheProblemsSpec spec)

    /**
     * Asserts that the report contains no problems. This passes if the report is not present.
     */
    void assertHasNoProblems() {
        assertContents {
            totalProblemsCount = 0
        }
    }

    private static class NoReportFixture extends ConfigurationCacheReportFixture {
        private final File projectRoot

        NoReportFixture(File projectRoot) {
            this.projectRoot = projectRoot
        }

        @Override
        protected void assertContents(HasConfigurationCacheProblemsSpec spec) {
            assert !spec.hasProblems():
                "Expected report to have problems but no report is available in '$projectRoot'"
            assert !needsReport(spec.inputs):
                "Expected report to have inputs but no report is available in '$projectRoot'"
            assert !needsReport(spec.incompatibleTasks):
                "Expected report to have incompatible task but no report is available in '$projectRoot'"
        }

        private boolean needsReport(ItemSpec itemSpec) {
            return itemSpec != ItemSpec.IGNORING && itemSpec != ItemSpec.EXPECTING_NONE
        }

        @Override
        void assertHasNoProblems() {}
    }

    private static class ExistingReportFixture extends ConfigurationCacheReportFixture {
        private final File reportFile
        private final Map<String, Object> jsModel

        ExistingReportFixture(File reportFile) {
            this.reportFile = reportFile
            this.jsModel = readJsModelFrom(reportFile)
        }

        @Override
        String toString() {
            return "CC Report with ${(jsModel.diagnostics as List).size()} entries at $reportFile"
        }

        protected static Map<String, Object> readJsModelFrom(File reportFile) {
            assertTrue("HTML report HTML file '$reportFile' not found", reportFile.isFile())

            // ConfigurationCacheReport ensures the pure json model can be read
            // by looking for `// begin-report-data` and `// end-report-data`
            def jsonText = linesBetween(reportFile, '// begin-report-data', '// end-report-data')
            assert jsonText: "malformed report file"
            new JsonSlurper().parseText(jsonText) as Map<String, Object>
        }

        private static String linesBetween(File file, String beginLine, String endLine) {
            return file.withReader('utf-8') { reader ->
                reader.lines().iterator()
                    .dropWhile { it != beginLine }
                    .drop(1)
                    .takeWhile { it != endLine }
                    .collect()
                    .join('\n')
            }
        }

        @Override
        protected void assertContents(HasConfigurationCacheProblemsSpec spec) {
            assertProblemsHtmlReport(spec)
            assertInputs(spec)
            assertIncompatibleTasks(spec)
        }

        private void assertInputs(HasConfigurationCacheProblemsSpec spec) {
            assertItems('input', spec.inputs)
        }

        private void assertIncompatibleTasks(HasConfigurationCacheProblemsSpec spec) {
            assertItems('incompatibleTask', spec.incompatibleTasks)
        }

        private void assertItems(String kind, ItemSpec spec) {
            if (spec == ItemSpec.IGNORING) {
                return
            }

            List<Matcher<String>> expectedItems = spec instanceof ItemSpec.ExpectingSome
                ? spec.itemMatchers.collect()
                : []


            List<Map<String, Object>> items = (jsModel.diagnostics as List<Map<String, Object>>).findAll { it[kind] != null }
            List<String> unexpectedItems = items.collect { formatItemForAssert(it, kind) }.reverse()
            for (int i in expectedItems.indices.reverse()) {
                def expectedItem = expectedItems[i]
                for (int j in unexpectedItems.indices) {
                    if (expectedItem.matches(unexpectedItems[j])) {
                        expectedItems.removeAt(i)
                        unexpectedItems.removeAt(j)
                        break
                    }
                }
            }
            if (!(spec instanceof ItemSpec.IgnoreUnexpected)) {
                assert unexpectedItems.isEmpty(): "Unexpected '$kind' items $unexpectedItems found in the report, expecting $expectedItems"
            }
            assert expectedItems.isEmpty(): "Expecting $expectedItems in the report, found $unexpectedItems"
        }

        private static String formatItemForAssert(Map<String, Object> item, String kind) {
            def trace = formatTrace(item['trace'][0])
            List<Map<String, Object>> itemFragments = item[kind]
            def message = formatStructuredMessage(itemFragments)
            "${trace}: ${message}"
        }

        private static String formatStructuredMessage(List<Map<String, Object>> fragments) {
            fragments.collect {
                // See StructuredMessage.Fragment
                it['text'] ?: "'${it['name']}'"
            }.join('')
        }

        private static String formatTrace(Map<String, Object> trace) {
            def kind = trace['kind']
            switch (kind) {
                case "Task": return trace['path']
                case "Bean": return trace['type']
                case "Field": return trace['name']
                case "InputProperty": return trace['name']
                case "OutputProperty": return trace['name']
                    // Build file 'build.gradle'
                case "BuildLogic": return trace['location'].toString().capitalize()
                case "BuildLogicClass": return trace['type']
                default: return "Gradle runtime"
            }
        }

        private void assertProblemsHtmlReport(HasConfigurationCacheProblemsSpec spec) {
            def totalProblemCount = spec.totalProblemsCount ?: spec.uniqueProblems.size()
            def problemsWithStackTraceCount = spec.problemsWithStackTraceCount == null ? totalProblemCount : spec.problemsWithStackTraceCount
            assert (spec.totalProblemsCount != null ||
                spec.problemsWithStackTraceCount != null ||
                !spec.uniqueProblems.empty ||
                spec.incompatibleTasks instanceof ItemSpec.ExpectingSome ||
                spec.inputs instanceof ItemSpec.ExpectingSome):
                "The spec suggests the report shouldn't be generated but it was"

            assertThat(
                "HTML report JS model has wrong number of total problem(s)",
                numberOfProblems(),
                equalTo(totalProblemCount)
            )
            assertThat(
                "HTML report JS model has wrong number of problem(s) with stacktrace",
                numberOfProblemsWithStacktrace(),
                equalTo(problemsWithStackTraceCount)
            )

            if (spec.checkReportProblems) {
                def problemMessages = problemMessages().unique()
                for (int i in spec.uniqueProblems.indices) {
                    // note that matchers for problem messages in report don't contain location prefixes
                    assert spec.uniqueProblems[i].matches(problemMessages[i]): "Expected problem at #$i to be ${spec.uniqueProblems[i]}, but was: ${problemMessages[i]}"
                }
            }
        }

        private int numberOfProblems() {
            return (jsModel.diagnostics as List<Object>).count { it['problem'] != null }
        }

        /**
         * Makes a best effort to collect problem messages from the JS model.
         */
        private List<String> problemMessages() {
            return (jsModel.diagnostics as List<Object>)
                .findAll { it['problem'] != null }
                .collect {
                    formatStructuredMessage(it['problem'])
                }
        }

        private int numberOfProblemsWithStacktrace() {
            return (jsModel.diagnostics as List<Object>).count { it['problem'] != null && it['error']?.getAt('parts') != null }
        }
    }

}
