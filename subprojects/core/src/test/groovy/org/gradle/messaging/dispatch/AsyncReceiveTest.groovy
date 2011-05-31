/*
 * Copyright 2010 the original author or authors.
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

public class AsyncReceiveTest extends ConcurrentSpecification {
    private final Dispatch<String> target1 = Mock()
    private final Dispatch<String> target2 = Mock()
    private final Receive<String> source1 = Mock()
    private final Receive<String> source2 = Mock()
    private final AsyncReceive<String> dispatch = new AsyncReceive<String>(executor)

    def cleanup() {
        dispatch?.stop()
    }

    def "dispatches message to target until end of stream reached"() {
        def endOfStream = startsAsyncAction()

        when:
        endOfStream.started {
            dispatch.dispatchTo(target1)
            dispatch.receiveFrom(source1)
        }
        finished()

        then:
        3 * source1.receive() >>> ['message1', 'message2', null]
        1 * target1.dispatch('message1')
        1 * target1.dispatch('message2') >> { endOfStream.done() }
    }

    def "can receive from multiple sources"() {
        def endOfStream = startsAsyncAction()

        when:
        endOfStream.started {
            dispatch.dispatchTo(target1)
            dispatch.receiveFrom(source1)
            dispatch.receiveFrom(source2)
        }
        finished()

        then:
        2 * source1.receive() >>> ['message1', null]
        2 * source2.receive() >>> ['message2', null]
        1 * target1.dispatch('message1')
        1 * target1.dispatch('message2') >> { endOfStream.done() }
    }

    def "receive waits until dispatch available"() {
        def received = startsAsyncAction()

        when:
        received.started {
            dispatch.receiveFrom(source1)
            dispatch.dispatchTo(target1)
        }
        finished()

        then:
        3 * source1.receive() >>> ['message1', 'message2', null]
        1 * target1.dispatch('message1')
        1 * target1.dispatch('message2') >> { received.done() }
    }

    def "can dispatch to multiple targets"() {
        def received = startsAsyncAction()

        when:
        received.started {
            dispatch.receiveFrom(source1)
            dispatch.dispatchTo(target1)
            dispatch.dispatchTo(target2)
        }
        finished()

        then:
        3 * source1.receive() >>> ['message1', 'message2', null]
        1 * _.dispatch('message1')
        1 * _.dispatch('message2') >> { received.done() }
        0 * target1._
        0 * target2._
    }

    def "stop blocks until all receive calls have completed"() {
        def receiving = startsAsyncAction()
        def stopped = waitsForAsyncActionToComplete()

        when:
        receiving.started {
            dispatch.dispatchTo(target1)
            dispatch.receiveFrom(source1)
        }
        stopped.start {
            dispatch.stop()
        }

        then:
        1 * source1.receive() >> { receiving.done(); stopped.done(); return null }
    }

    def "stop blocks until all dispatch calls have completed"() {
        def receiving = startsAsyncAction()
        def stopped = waitsForAsyncActionToComplete()

        when:
        receiving.started {
            dispatch.dispatchTo(target1)
            dispatch.receiveFrom(source1)
        }
        stopped.start {
            dispatch.stop()
        }

        then:
        1 * source1.receive() >>> ['message', null]
        1 * target1.dispatch('message') >> { receiving.done(); stopped.done() }
    }

    def "can stop when no dispatch provided"() {
        given:
        dispatch.receiveFrom(source1)

        expect:
        dispatch.stop()
    }
}
