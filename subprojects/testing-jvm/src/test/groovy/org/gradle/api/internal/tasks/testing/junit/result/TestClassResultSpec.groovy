/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result

import org.gradle.api.internal.tasks.testing.results.DefaultTestResult
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification

class TestClassResultSpec extends Specification {

    def "provides test class result information"() {
        def result = new TestClassResult(1, 'class', 100)
        assert result.duration == 0

        when:
        result.add(new TestMethodResult(1, "foo").completed(new DefaultTestResult(TestResult.ResultType.SUCCESS, 100, 200, 1, 1, 0, [])))
        result.add(new TestMethodResult(2, "fail").completed(new DefaultTestResult(TestResult.ResultType.FAILURE, 250, 300, 1, 0, 1, [new RuntimeException("bar")])))
        result.add(new TestMethodResult(3, "fail2").completed(new DefaultTestResult(TestResult.ResultType.FAILURE, 300, 450, 1, 0, 1, [new RuntimeException("foo")])))

        then:
        result.failuresCount == 2
        result.testsCount == 3
        result.duration == 350
    }
}
