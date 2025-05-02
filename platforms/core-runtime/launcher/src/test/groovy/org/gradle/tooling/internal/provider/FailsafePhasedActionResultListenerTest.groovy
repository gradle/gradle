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

package org.gradle.tooling.internal.provider

import org.gradle.internal.event.ListenerNotificationException
import spock.lang.Specification
import org.gradle.tooling.internal.protocol.PhasedActionResult
import org.gradle.tooling.internal.protocol.PhasedActionResultListener

class FailsafePhasedActionResultListenerTest extends Specification {
    def delegateListener = Mock(PhasedActionResultListener)
    def event = Stub(PhasedActionResult)
    def failsafeListener = new FailsafePhasedActionResultListener(delegateListener)

    def "delegate to listener"() {
        when:
        failsafeListener.onResult(event)

        then:
        1 * delegateListener.onResult(event)
    }

    def "failure is caught"() {
        def failure = new RuntimeException()
        given:
        delegateListener.onResult(_) >> { throw failure }

        when:
        failsafeListener.onResult(event)

        then:
        noExceptionThrown()

        when:
        failsafeListener.rethrowErrors()

        then:
        ListenerNotificationException e = thrown()
        e.message == 'One or more build phasedAction listeners failed with an exception.'
        e.causes.size() == 1
        e.cause == failure
    }
}
