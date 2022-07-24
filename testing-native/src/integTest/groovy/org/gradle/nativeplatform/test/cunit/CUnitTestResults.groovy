/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.test.cunit

import groovy.xml.XmlParser
import org.gradle.test.fixtures.file.TestFile

class CUnitTestResults {
    TestFile testResultsFile
    Node resultsNode
    Map<String, Suite> suites = [:]
    Map<String, SummaryRecord> summaryRecords = [:]

    CUnitTestResults(TestFile testResultsFile) {
        assert testResultsFile.exists()
        this.testResultsFile = testResultsFile
        final XmlParser parser = new XmlParser(false, false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parser.setFeature("http://xml.org/sax/features/namespaces", false)
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        this.resultsNode = parser.parse(testResultsFile)

        resultsNode.CUNIT_RESULT_LISTING.CUNIT_RUN_SUITE.CUNIT_RUN_SUITE_SUCCESS.each { Node suiteNode ->
            def suite = new Suite(suiteNode)
            suites.put(suite.name, suite)
        }

        summaryRecords['Suites'] = new SummaryRecord(resultsNode.CUNIT_RUN_SUMMARY.CUNIT_RUN_SUMMARY_RECORD.find({it.TYPE.text() == 'Suites'}) as Node)
        summaryRecords['Test Cases'] = new SummaryRecord(resultsNode.CUNIT_RUN_SUMMARY.CUNIT_RUN_SUMMARY_RECORD.find({it.TYPE.text() == 'Test Cases'}) as Node)
        summaryRecords['Assertions'] = new SummaryRecord(resultsNode.CUNIT_RUN_SUMMARY.CUNIT_RUN_SUMMARY_RECORD.find({it.TYPE.text() == 'Assertions'}) as Node)
    }

    List<String> getSuiteNames() {
        suites.keySet() as List
    }

    def checkTestCases(int total, int succeeded, int failed) {
        checkSummaryRecord('Test Cases', total, succeeded, failed)
    }

    def checkAssertions(int total, int succeeded, int failed) {
        checkSummaryRecord('Assertions', total, succeeded, failed)
    }

    private def checkSummaryRecord(String name, int total, int succeeded, int failed) {
        def recordNode = resultsNode.CUNIT_RUN_SUMMARY.CUNIT_RUN_SUMMARY_RECORD.find({it.TYPE.text().trim() == name}) as Node
        assert recordNode.RUN.text() as int == total
        assert recordNode.SUCCEEDED.text() as int == succeeded
        assert recordNode.FAILED.text() as int == failed
        return true
    }

    class Suite {
        final Node suiteNode

        Suite(Node suiteNode) {
            this.suiteNode = suiteNode
        }

        String getName() {
            return suiteNode.SUITE_NAME.text().trim()
        }

        List<String> getPassingTests() {
            suiteNode.CUNIT_RUN_TEST_RECORD.CUNIT_RUN_TEST_SUCCESS.TEST_NAME*.text()*.trim()
        }

        List<String> getFailingTests() {
            suiteNode.CUNIT_RUN_TEST_RECORD.CUNIT_RUN_TEST_FAILURE.TEST_NAME*.text()*.trim().unique()
        }
    }

    class SummaryRecord {
        final Node recordNode

        SummaryRecord(Node recordNode) {
            this.recordNode = recordNode
        }

        int getRun() {
            recordNode.RUN.text() as int
        }

        int getSucceeded() {
            recordNode.SUCCEEDED.text() as int
        }

        int getFailed() {
            recordNode.FAILED.text() as int
        }

    }
}
