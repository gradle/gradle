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
package org.gradle.api.internal.tasks.testing.report

import spock.lang.Specification

import static org.gradle.api.tasks.testing.TestResult.ResultType.*

class CompositeTestResultsTest extends Specification {
    final CompositeTestResults results = new CompositeTestResults(null) {
        @Override
        String getTitle() {
            throw new UnsupportedOperationException()
        }

        @Override
        String getBaseUrl() {
            return "test/page.html"
        }
    }

    def formatsSuccessRateWhenNoTests() {
        expect:
        results.successRate == null
        results.formattedSuccessRate == "-"
    }

    def formatsSuccessRateWhenAllTestsPass() {
        results.addTest(test())

        expect:
        results.successRate == 100
        results.formattedSuccessRate == '100%'
    }

    def formatsSuccessRateWhenSomeTestsFail() {
        def failed = results.addTest(test())
        results.failed(failed)
        results.addTest(test())
        results.addTest(test())

        expect:
        results.successRate == 66
        results.formattedSuccessRate == '66%'
    }

    def formatsSuccessRateWhenSomeTestsFailAndSomeTestsAreIgnored() {
        def failed = results.addTest(test())
        results.failed(failed)
        results.addTest(test())
        results.addTest(test())
        results.ignored(test());

        expect:
        results.successRate == 50
        results.formattedSuccessRate == '50%'
    }

    def formatsSuccessRateWhenAllTestsFail() {
        def failed = results.addTest(test())
        results.failed(failed)

        expect:
        results.successRate == 0
        results.formattedSuccessRate == '0%'
    }

    def formatsSuccessRateWhenAllTestsAreIgnored() {
        results.addTest(test())
        results.addTest(test())
        results.ignored(test());
        results.ignored(test());

        expect:
        results.successRate == null
        results.formattedSuccessRate == '-'
    }

    def formatsDurationWhenNoTests() {
        expect:
        results.formattedDuration == '-'
    }

    def formatsDurationWhenTests() {
        results.addTest(test())

        expect:
        results.formattedDuration == '0.045s'
    }

    def computesResultTypeWhenOnlySuccess() {
        results.addTest(test())

        expect:
        results.resultType == SUCCESS;
    }

    def computesResultTypeWhenSuccessAndIgnored() {
        results.addTest(test())
        results.addTest(test())
        results.ignored(test())

        expect:
        results.resultType == SKIPPED;
    }

    def computesResultTypeWhenSuccessAndIgnoredAndFailed() {
        results.addTest(test())
        results.addTest(test())
        results.ignored(test())
        def failed = results.addTest(test())
        results.failed(failed)

        expect:
        results.resultType == FAILURE;
    }

    def calculatesRelativePath() {
        def other = Stub(CompositeTestResults) {
            getBaseUrl() >> fromUrl
        }

        expect:
        results.getUrlTo(other) == relativeUrl

        where:
        fromUrl                  | relativeUrl
        "test/other.html"        | "other.html"
        "other/other.html"       | "../other/other.html"
        "index.html"             | "../index.html"
        "test/subdir/other.html" | "subdir/other.html"
    }

    private TestResult test() {
        return new TestResult('test', 45, new ClassTestResults(1, 'test', null))
    }
}
