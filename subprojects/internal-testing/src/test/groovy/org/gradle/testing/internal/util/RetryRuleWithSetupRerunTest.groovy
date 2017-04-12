/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testing.internal.util

import org.junit.Rule
import spock.lang.Specification

import static org.gradle.testing.internal.util.RetryRule.retryIf

class RetryRuleWithSetupRerunTest extends SuperSpecification {

    @Rule
    RetryRule retryRule = retryIf({ t -> t instanceof IOException })

    int iteration = 0

    int setupCallCount = 0

    def setup() {
        setupCallCount++
    }

    def "reruns all setup methods if specification is passed to rule"() {
        given:
        iteration++

        when:
        throwWhen(new IOException(), iteration < 2)

        then:
        setupCallCount == 2
        superSetupCallCount == 2
    }

    def "reruns all setup methods if specification is passed to rule for both retries"() {
        given:
        iteration++

        when:
        throwWhen(new IOException(), iteration < 3)

        then:
        setupCallCount == 3
        superSetupCallCount == 3
    }

    private static void throwWhen(Throwable throwable, boolean condition) {
        if (condition) {
            throw throwable;
        }
    }
}

class SuperSpecification extends Specification {
    int superSetupCallCount = 0

    def setup() {
        superSetupCallCount++
    }
}

