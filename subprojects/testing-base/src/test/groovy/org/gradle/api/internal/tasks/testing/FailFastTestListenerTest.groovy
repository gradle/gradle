/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.Unroll

class FailFastTestListenerTest extends Specification {
    TestExecuter testExecuter = Mock()
    TestDescriptor testDescriptor = Mock()
    TestResult testResult = Mock()

    FailFastTestListener unit

    def setup() {
        unit = new FailFastTestListener(testExecuter)
    }

    def "afterSuite calls stopNow on failure"() {
        when:
        testResult.getResultType() >> TestResult.ResultType.FAILURE
        unit.afterSuite(testDescriptor, testResult)

        then:
        1 * testExecuter.stopNow()
    }

    @Unroll
    def "afterSuite ignores #result result"() {
        when:
        testResult.getResultType() >> result
        unit.afterSuite(testDescriptor, testResult)

        then:
        0 * testExecuter.stopNow()

        where:
        result << TestResult.ResultType.values() - TestResult.ResultType.FAILURE
    }

    def "afterTest calls stopNow on failure"() {
        when:
        testResult.getResultType() >> TestResult.ResultType.FAILURE
        unit.afterTest(testDescriptor, testResult)

        then:
        1 * testExecuter.stopNow()
    }

    @Unroll
    def "afterTest ignores #result result"() {
        when:
        testResult.getResultType() >> result
        unit.afterTest(testDescriptor, testResult)

        then:
        0 * testExecuter.stopNow()

        where:
        result << TestResult.ResultType.values() - TestResult.ResultType.FAILURE
    }
}
