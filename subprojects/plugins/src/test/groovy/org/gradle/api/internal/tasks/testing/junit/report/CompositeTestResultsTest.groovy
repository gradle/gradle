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

import spock.lang.Specification

class CompositeTestResultsTest extends Specification {
    final CompositeTestResults results = new CompositeTestResults(null) {
        @Override
        String getTitle() {
            throw new UnsupportedOperationException()
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

    def formatsDurationWhenNoTests() {
        expect:
        results.formattedDuration == '-'
    }

    def formatsDurationWhenTests() {
        results.addTest(test())

        expect:
        results.formattedDuration == '0.045s'
    }

    private TestResult test() {
        return new TestResult('test', 45, new ClassTestResults(1, 'test', null))
    }
}
