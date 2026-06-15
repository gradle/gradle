/*
 * Copyright 2026 the original author or authors.
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

import spock.lang.Specification

class DefaultTestFailureTest extends Specification {
    def "fromTestFrameworkStartupFailure produces details whose isFrameworkStartupFailure() returns true"() {
        def cause = new RuntimeException("boom")

        when:
        def failure = DefaultTestFailure.fromTestFrameworkStartupFailure(cause)

        then:
        failure.details.frameworkStartupFailure
        !failure.details.assertionFailure
        !failure.details.assumptionFailure
        !failure.details.fileComparisonFailure
        failure.details.className == 'java.lang.RuntimeException'
        failure.details.message == 'boom'
    }

    def "fromTestFrameworkFailure produces details whose isFrameworkStartupFailure() returns false"() {
        def cause = new RuntimeException("boom")

        when:
        def failure = DefaultTestFailure.fromTestFrameworkFailure(cause, null)

        then:
        !failure.details.frameworkStartupFailure
        !failure.details.assertionFailure
        !failure.details.assumptionFailure
        !failure.details.fileComparisonFailure
    }

    def "fromTestAssertionFailure produces details whose isFrameworkStartupFailure() returns false"() {
        def cause = new AssertionError("nope")

        when:
        def failure = DefaultTestFailure.fromTestAssertionFailure(cause, 'expected', 'actual', null)

        then:
        !failure.details.frameworkStartupFailure
        failure.details.assertionFailure
    }
}
