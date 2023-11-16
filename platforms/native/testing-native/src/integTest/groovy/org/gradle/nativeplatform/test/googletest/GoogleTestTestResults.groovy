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
package org.gradle.nativeplatform.test.googletest

import groovy.xml.XmlParser
import org.gradle.test.fixtures.file.TestFile

class GoogleTestTestResults {
    TestFile testResultsFile
    Node resultsNode
    Map<String, Suite> suites = [:]

    GoogleTestTestResults(TestFile testResultsFile) {
        assert testResultsFile.exists()
        this.testResultsFile = testResultsFile
        final XmlParser parser = new XmlParser(false, false)
        parser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        parser.setFeature("http://xml.org/sax/features/namespaces", false)
        parser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        this.resultsNode = parser.parse(testResultsFile)

        resultsNode.testsuite.each { Node suiteNode ->
            def suite = new Suite(suiteNode)
            suites.put(suite.name, suite)
        }
    }

    List<String> getSuiteNames() {
        suites.keySet() as List
    }

    def checkTestCases(int total, int succeeded, int failed) {
        def tests = resultsNode.@tests as int
        def disabled = resultsNode.@disabled as int
        def testFailed = resultsNode.@failures as int
        def run = tests - disabled
        def testSucceeded = tests - failed
        assert run == total
        assert testSucceeded == succeeded
        assert testFailed == failed
        return true
    }

    class Suite {
        final Node suiteNode

        Suite(Node suiteNode) {
            this.suiteNode = suiteNode
        }

        String getName() {
            return suiteNode.@name
        }

        List<String> getPassingTests() {
            suiteNode.testcase.findAll({ it.failure.size() == 0 })*.@name.sort()
        }

        List<String> getFailingTests() {
            suiteNode.testcase.findAll({ it.failure.size() != 0 })*.@name.sort()
        }
    }
}
