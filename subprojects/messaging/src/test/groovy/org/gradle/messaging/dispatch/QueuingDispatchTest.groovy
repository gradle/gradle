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

import org.gradle.util.ConcurrentSpecification
import spock.lang.Ignore

class QueuingDispatchTest extends ConcurrentSpecification {
    final Dispatch<String> target = Mock()
    final QueuingDispatch<String> dispatch = new QueuingDispatch<String>()

    def "queues messages until receiving dispatch connected"() {
        when:
        dispatch.dispatch("a")
        dispatch.dispatch("b")

        then:
        0 * target._

        when:
        dispatch.dispatchTo(target)

        then:
        1 * target.dispatch("a")
        1 * target.dispatch("b")
        0 * target._
    }

    def "dispatches messages directly to receiving dispatch after it has connected"() {
        given:
        dispatch.dispatchTo(target)

        when:
        dispatch.dispatch("a")

        then:
        1 * target.dispatch("a")
        0 * target._
    }

    @Ignore
    def "stop blocks until queued messages delivered to receiving dispatch"() {
        expect: false
    }

    @Ignore
    def "stop blocks until message delivered to receiving dispatch"() {
        expect: false
    }

    @Ignore
    def "stop does not block if no messages have been dispatched"() {
        expect: false
    }

    @Ignore
    def "cannot connect receiving dispatch after stop"() {
        expect: false
    }

    @Ignore
    def "delivers messages by a single thread at a time"() {
        expect: false
    }
}
