/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report

import org.gradle.performance.ResultSpecification
import org.gradle.performance.results.CrossVersionResultsStore
import org.gradle.performance.results.ResultsStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule

class ReportGeneratorTest extends ResultSpecification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule
    SetSystemProperties properties = new SetSystemProperties("org.gradle.performance.db.url": "jdbc:h2:" + tmpDir.testDirectory)
    final dbFile = tmpDir.file("results")
    final reportDir = tmpDir.file("report")
    final resultsJson = tmpDir.file('results.json')

    def setup() {
        resultsJson << '[]'
    }

    def "generates report"() {
        setup:
        def store = new CrossVersionResultsStore(dbFile.name)
        def generator = new DefaultReportGenerator() {
            @Override
            ResultsStore getResultsStore() {
                return store
            }
        }
        def result2 = crossVersionResults()
        result2.current << operation()
        result2.current << operation()
        store.report(result2)

        when:
        generator.generateReport(reportDir.absolutePath, resultsJson.absolutePath, 'testProject')

        then:
        reportDir.file("index.html").isFile()

        cleanup:
        store.close()
    }
}
