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

package org.gradle.internal.remote.internal.hub.queue

import org.gradle.internal.remote.internal.hub.protocol.EndOfStream

class MultiEndPointQueueTest extends AbstractQueueTest {
    final MultiEndPointQueue queue = new MultiEndPointQueue(lock)

    def "forwards queued unicast messages to first waiting endpoint"() {
        given:
        def message1 = unicast()
        def message2 = unicast()
        def endpoint = queue.newEndpoint()

        and:
        queue.dispatch(message1)
        queue.dispatch(message2)

        when:
        queue.empty(endpoint)
        def messages = []
        endpoint.take(messages)

        then:
        messages == [message1, message2]
    }

    def "forwards unicast message to first waiting endpoint"() {
        given:
        def message = unicast()
        def endpoint = queue.newEndpoint()

        and:
        queue.empty(endpoint)

        when:
        queue.dispatch(message)
        def messages = []
        endpoint.take(messages)

        then:
        messages == [message]
    }

    def "forwards queued broadcast messages to all endpoints"() {
        given:
        def message1 = unicast()
        def message2 = broadcast()
        def message3 = unicast()
        def endpoint1 = queue.newEndpoint()
        def endpoint2 = queue.newEndpoint()

        and:
        queue.dispatch(message1)
        queue.dispatch(message2)
        queue.dispatch(message3)

        when:
        queue.empty(endpoint1)
        def messages1 = []
        endpoint1.take(messages1)
        def messages2 = []
        endpoint2.take(messages2)

        then:
        messages1 == [message1, message2, message3]
        messages2 == [message2]
    }

    def "forwards broadcast messages to all endpoints when nothing queued and nothing waiting"() {
        given:
        def message = broadcast()
        def message2 = unicast()
        def endpoint1 = queue.newEndpoint()
        def endpoint2 = queue.newEndpoint()

        when:
        queue.dispatch(message)
        queue.dispatch(message2)
        def messages1 = []
        endpoint1.take(messages1)
        def messages2 = []
        endpoint2.take(messages2)

        then:
        messages1 == [message]
        messages2 == [message]
    }

    def "forwards broadcast messages to all endpoints when nothing queued"() {
        given:
        def message = broadcast()
        def message2 = unicast()
        def endpoint1 = queue.newEndpoint()
        def endpoint2 = queue.newEndpoint()

        and:
        queue.empty(endpoint1)
        queue.empty(endpoint2)

        when:
        queue.dispatch(message)
        queue.dispatch(message2)
        def messages1 = []
        endpoint1.take(messages1)
        def messages2 = []
        endpoint2.take(messages2)

        then:
        messages1 == [message]
        messages2 == [message]
    }

    def "buffers messages when there are no endpoints"() {
        given:
        def message1 = broadcast()
        def message2 = unicast()
        def message3 = unicast()

        and:
        queue.dispatch(message1)
        queue.dispatch(message2)
        queue.dispatch(message3)

        when:
        def endpoint = queue.newEndpoint()
        def messages = []
        endpoint.take(messages)

        then:
        messages == [message1, message2, message3]
    }

    def "does not dispatch anything to endpoint that has stopped"() {
        given:
        def endpoint = queue.newEndpoint()
        queue.empty(endpoint)

        when:
        endpoint.stop()
        queue.dispatch(broadcast())
        queue.dispatch(unicast())
        def messages = []
        endpoint.take(messages)

        then:
        messages.size() == 1
        messages[0] instanceof EndOfStream
    }
}
