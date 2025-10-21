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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ConfigurationCacheProblemsFixtureTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider dirProvider = new TestNameTestDirectoryProvider(getClass())

    def rootDir = dirProvider.createDir()
    def reportDir = dirProvider.file("build", "reports", "configuration-cache")
    def reportFile = reportDir.file("configuration-cache-report.html")
    def fixture = new ConfigurationCacheProblemsFixture(rootDir)

    def "assertHtmlReportHasNoProblems passes when there are no problems"() {
        reportFile.setText("""
// begin-report-data
{
    "diagnostics": []
}
// end-report-data
        """)

        expect:
        fixture.assertHtmlReportHasNoProblems()
    }

    def "assertHtmlReportHasNoProblems fails when there are problems"() {
        reportFile.setText("""
// begin-report-data
{
    "diagnostics": [
        {
            "problem": [{
                "text": "Some problem"
            }]
        }
    ]
}
// end-report-data
        """)

        when:
        fixture.assertHtmlReportHasNoProblems()

        then:
        def expectedFailure = thrown(java.lang.AssertionError)
        expectedFailure.message == "HTML report JS model has wrong number of total problem(s)\nExpected: <0>\n     but: was <1>"
    }

    def "assertHtmlReportHasNoProblems fails when there is no report"() {
        when:
        fixture.assertHtmlReportHasNoProblems()

        then:
        def expectedFailure = thrown(java.lang.AssertionError)
        expectedFailure.message.startsWith("Configuration cache report directory not found at ${reportDir}.")
    }
}
