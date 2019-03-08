/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps


import org.gradle.api.InvalidUserDataException
import org.gradle.internal.execution.Context
import org.gradle.internal.execution.Result
import org.gradle.internal.execution.timeout.Timeout
import org.gradle.internal.execution.timeout.TimeoutHandler

import java.time.Duration
import java.time.temporal.ChronoUnit

class TimeoutStepTest extends StepSpec {
    def timeoutHandler = Mock(TimeoutHandler)
    def step = new TimeoutStep<Context>(timeoutHandler, delegate)
    def context = Mock(Context)
    def delegateResult = Mock(Result)

    def "negative timeout is reported"() {
        when:
        step.execute(context)

        then:
        thrown InvalidUserDataException

        1 * context.work >> work
        1 * work.timeout >> Optional.of(Duration.of(-1, ChronoUnit.SECONDS))
        1 * work.displayName >> "bad work"
        0 * _
    }

    def "executing without timeout succeeds"() {
        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * work.timeout >> Optional.empty()

        then:
        1 * delegate.execute(context) >> delegateResult
        0 * _
    }

    def "executing under timeout succeeds"() {
        def duration = Duration.of(1, ChronoUnit.SECONDS)
        def timeout = Mock(Timeout)

        when:
        def result = step.execute(context)

        then:
        result == delegateResult

        1 * context.work >> work
        1 * work.timeout >> Optional.of(duration)

        then:
        timeoutHandler.start(_ as Thread, duration) >> timeout

        then:
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * timeout.stop() >> false
        0 * _
    }

    def "executing over timeout fails"() {
        def duration = Duration.of(1, ChronoUnit.SECONDS)
        def timeout = Mock(Timeout)

        when:
        step.execute(context)

        then:
        1 * context.work >> work
        1 * work.timeout >> Optional.of(duration)

        then:
        1 * timeoutHandler.start(_ as Thread, duration) >> timeout

        then:
        1 * delegate.execute(context) >> delegateResult

        then:
        1 * timeout.stop() >> true

        then:
        def ex = thrown Exception
        ex.message == "Timeout has been exceeded"
        0 * _
    }
}
