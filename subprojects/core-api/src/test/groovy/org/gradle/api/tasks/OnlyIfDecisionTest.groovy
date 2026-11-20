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

package org.gradle.api.tasks

import spock.lang.Specification

/**
 * Tests the factory methods, normalization, and value semantics of {@link OnlyIfDecision}.
 */
class OnlyIfDecisionTest extends Specification {
    def "proceed runs the task without a skip reason"() {
        expect:
        OnlyIfDecision.proceed().shouldRun()
        OnlyIfDecision.proceed().skipReason == null
        OnlyIfDecision.proceed().is(OnlyIfDecision.proceed())
        OnlyIfDecision.proceed().toString() == "OnlyIfDecision.proceed()"
    }

    def "skip(#reason) skips the task without a skip reason"() {
        when:
        def decision = OnlyIfDecision.skip(reason)

        then:
        !decision.shouldRun()
        decision.skipReason == null
        decision.is(OnlyIfDecision.skip())
        decision.toString() == "OnlyIfDecision.skip()"

        where:
        reason << [null, "", "   ", "\t\n"]
    }

    def "skip with reason skips the task with that reason"() {
        when:
        def decision = OnlyIfDecision.skip(" on CI ")

        then:
        !decision.shouldRun()
        decision.skipReason == " on CI "
        decision.toString() == "OnlyIfDecision.skip(' on CI ')"
    }

    def "decisions are equal when they have the same outcome and reason"() {
        expect:
        (left == right) == equal
        !equal || left.hashCode() == right.hashCode()

        where:
        left                             | right                            | equal
        OnlyIfDecision.skip("a")         | OnlyIfDecision.skip("a")         | true
        OnlyIfDecision.skip("a")         | OnlyIfDecision.skip("b")         | false
        OnlyIfDecision.skip("a")         | OnlyIfDecision.skip()            | false
        OnlyIfDecision.skip()            | OnlyIfDecision.proceed()         | false
        OnlyIfDecision.proceed()         | OnlyIfDecision.proceed()         | true
    }
}
