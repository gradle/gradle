/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.operations

import org.gradle.util.internal.TextUtil
import spock.lang.Specification


class MultipleBuildOperationFailuresTest extends Specification {

    public static final String LOG_LOCATION = "<log location>"
    static class TestException extends BuildOperationFailure {
        protected TestException(int count) {
            super(null, "<test message $count>")
        }
    }

    def "format for a single failure"() {
        given:
        def failures = [ new TestException(0) ]
        def exception = new MultipleBuildOperationFailures(failures, LOG_LOCATION)
        when:
        def message = exception.getMessage()
        then:
        TextUtil.normaliseLineSeparators(message) == """A build operation failed.
    <test message 0>
See the complete log at: $LOG_LOCATION"""
    }

    def "format for multiple failures at limit"() {
        given:
        def failures = (0..9).collect { new TestException(it) }
        def exception = new MultipleBuildOperationFailures(failures, LOG_LOCATION)
        when:
        def message = exception.getMessage()
        then:
        TextUtil.normaliseLineSeparators(message) == """Multiple build operations failed.
    <test message 0>
    <test message 1>
    <test message 2>
    <test message 3>
    <test message 4>
    <test message 5>
    <test message 6>
    <test message 7>
    <test message 8>
    <test message 9>
See the complete log at: $LOG_LOCATION"""
    }

    def "format for multiple failures just over limit"() {
        given:
        def failures = (0..10).collect { new TestException(it) }
        def exception = new MultipleBuildOperationFailures(failures, LOG_LOCATION)
        when:
        def message = exception.getMessage()
        then:
        TextUtil.normaliseLineSeparators(message) == """Multiple build operations failed.
    <test message 0>
    <test message 1>
    <test message 2>
    <test message 3>
    <test message 4>
    <test message 5>
    <test message 6>
    <test message 7>
    <test message 8>
    <test message 9>
    ...and 1 more failure.
See the complete log at: $LOG_LOCATION"""
    }

    def "format for multiple failures beyond limit"() {
        given:
        def failures = (0..14).collect { new TestException(it) }
        def exception = new MultipleBuildOperationFailures(failures, LOG_LOCATION)
        when:
        def message = exception.getMessage()
        then:
        TextUtil.normaliseLineSeparators(message) == """Multiple build operations failed.
    <test message 0>
    <test message 1>
    <test message 2>
    <test message 3>
    <test message 4>
    <test message 5>
    <test message 6>
    <test message 7>
    <test message 8>
    <test message 9>
    ...and 5 more failures.
See the complete log at: $LOG_LOCATION"""
    }

    def "omits location when null"() {
        given:
        def failures = (0..14).collect { new TestException(it) }
        def exception = new MultipleBuildOperationFailures(failures, null)
        when:
        def message = exception.getMessage()
        then:
        TextUtil.normaliseLineSeparators(message) == """Multiple build operations failed.
    <test message 0>
    <test message 1>
    <test message 2>
    <test message 3>
    <test message 4>
    <test message 5>
    <test message 6>
    <test message 7>
    <test message 8>
    <test message 9>
    ...and 5 more failures."""
    }
}
