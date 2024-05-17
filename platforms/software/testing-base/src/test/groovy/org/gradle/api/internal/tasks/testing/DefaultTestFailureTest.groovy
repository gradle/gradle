/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.tasks.testing.TestFailure
import org.gradle.internal.serialize.PlaceholderAssertionError
import org.gradle.internal.serialize.PlaceholderException
import spock.lang.Specification

class DefaultTestFailureTest extends Specification {

    def "uses original exception class name when created with PlaceholderException"() {
        given:
        def placeholder = new PlaceholderException("com.acme.OriginalException", "custom message", null, null, null, null)

        when:
        def failure = TestFailure.fromTestFrameworkFailure(placeholder)

        then:
        with(failure) {
            causes.empty
            rawFailure == placeholder
            with(details) {
                message == "custom message"
                className == "com.acme.OriginalException"
                !assertionFailure
                stacktrace != null
                expected == null
                actual == null
            }
        }
    }

    def "uses original exception class name when created with PlaceholderAssertionError"() {
        given:
        def placeholder = new PlaceholderAssertionError("com.acme.OriginalAssertionError", "custom message", null, null, null, null)

        when:
        def failure = TestFailure.fromTestAssertionFailure(placeholder, "expected", "actual")

        then:
        with(failure) {
            causes.empty
            rawFailure == placeholder
            with(details) {
                message == "custom message"
                className == "com.acme.OriginalAssertionError"
                assertionFailure
                stacktrace != null
                expected == "expected"
                actual == "actual"
            }
        }
    }
}
