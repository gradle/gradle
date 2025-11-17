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

import spock.lang.Specification

import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED
import static org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS

class TestClassResultSpec extends Specification {

    def "provides test class result information"() {
        def result = new TestClassResult(1, 'class', 'class', 0, [])
        assert result.duration == 0

        when:
        result.add(new TestMethodResult(1, "foo", "foo", SUCCESS, 50L, 150, []))
        result.add(new TestMethodResult(2, "fail", "fail", FAILURE, 50L, 250, []))
        result.add(new TestMethodResult(3, "fail2", "fail2", FAILURE, 50L, 350, []))
        result.add(new TestMethodResult(4, "skip1", "skip1", SKIPPED, 50L, 525, []))
        result.add(new TestMethodResult(5, "skip2", "skip2", SKIPPED, 50L, 550, []))
        then:
        result.failuresCount == 2
        result.testsCount == 5
        result.duration == 550
    }
}
