/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.messaging.dispatch

import spock.lang.Specification

class FailureHandlingReceiveTest extends Specification {
    final Receive<String> target = Mock()
    final ReceiveFailureHandler handler = Mock()
    final FailureHandlingReceive<String> receive = new FailureHandlingReceive<String>(target, handler)

    def "receives from target"() {
        when:
        def message = receive.receive()

        then:
        message == 'message'
        1 * target.receive() >> 'message'
    }

    def "notifies handler on receive failure"() {
        def count = 0
        def failure = new RuntimeException()

        when:
        def message = receive.receive()

        then:
        message == 'message'
        2 * target.receive() >> {
            if (count == 0) {
                count++
                throw failure
            }
            return 'message'
        }
        1 * handler.receiveFailed(failure)
    }

    def "propagates exception thrown by handler"() {
        def failure = new RuntimeException()
        def adaptedFailure = new RuntimeException()

        when:
        receive.receive()

        then:
        RuntimeException e = thrown()
        e == adaptedFailure
        1 * target.receive() >> { throw failure }
        1 * handler.receiveFailed(failure) >> { throw adaptedFailure }
    }
}
