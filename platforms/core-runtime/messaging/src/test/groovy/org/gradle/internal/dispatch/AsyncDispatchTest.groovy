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
package org.gradle.internal.dispatch

import org.gradle.util.internal.MultithreadedTestRule
import org.junit.Rule
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.sameInstance

public class AsyncDispatchTest extends Specification {
    private final Dispatch<String> target1 = Mock()

    @Rule
    public MultithreadedTestRule parallel = new MultithreadedTestRule()
    private final AsyncDispatch<String> dispatch = new AsyncDispatch<String>(parallel.executor)

    def 'dispatches message to an idle target'() {
        when:
        dispatch.dispatchTo(target1)
        dispatch.dispatch('message1')
        dispatch.dispatch('message2')
        dispatch.stop()

        then:
        1 * target1.dispatch('message1')
        then:
        1 * target1.dispatch('message2')
    }

    def 'dispatch does not block while no idle target available'() {
        given:
        Dispatch<String> target1 = new DispatchStub(
                message1: {
                    parallel.syncAt(1)
                    parallel.syncAt(2)
                },
                message3: { parallel.syncAt(3) })
        Dispatch<String> target2 = new DispatchStub(
                message2: {
                    parallel.syncAt(2)
                    parallel.syncAt(3)
                })

        when:
        parallel.run {
            dispatch.dispatchTo(target1)
            dispatch.dispatch('message1')
            parallel.syncAt(1)

            dispatch.dispatchTo(target2)
            dispatch.dispatch('message2')
            parallel.syncAt(2)

            dispatch.dispatch('message3')
            parallel.syncAt(3)
        }

        dispatch.stop()

        then:
        target1.receivedMessages == ['message1', 'message3']
        target2.receivedMessages == ['message2']
    }

    def 'can stop from multiple threads'() {
        when:
        dispatch.dispatchTo(target1)

        parallel.start {
            dispatch.stop()
        }
        parallel.start {
            dispatch.stop()
        }

        then:
        noExceptionThrown()
    }

    def 'can request stop from multiple threads'() {
        when:
        dispatch.dispatchTo(target1)

        parallel.start {
            dispatch.requestStop()
        }
        parallel.start {
            dispatch.requestStop()
        }

        parallel.waitForAll()
        dispatch.stop()

        then:
        noExceptionThrown()
    }

    def 'stop blocks until all messages are dispatched'() {
        given:
        def target1 = new DispatchStub(
            message1: {
                parallel.syncAt(1)
                parallel.syncAt(2)
                parallel.syncAt(3)
            }
        )
        def target2 = new DispatchStub(
            message2: {
                parallel.syncAt(2)
                parallel.syncAt(3)
            }
        )

        when:
        parallel.run {
            dispatch.dispatchTo(target1)
            dispatch.dispatch('message1')
            parallel.syncAt(1)

            dispatch.dispatchTo(target2)
            dispatch.dispatch('message2')
            parallel.syncAt(2)

            parallel.expectBlocksUntil(3) {
                dispatch.stop()
            }
        }

        then:
        target1.receivedMessages == ['message1']
        target2.receivedMessages == ['message2']
    }

    def 'requestStop does not block when messages are queued'() {
        given:
        def target1 = new DispatchStub(
            message1: {
                parallel.syncAt(1)
                parallel.syncAt(2)
            }
        )

        when:
        parallel.run {
            dispatch.dispatchTo(target1)
            dispatch.dispatch('message1')
            parallel.syncAt(1)
            dispatch.requestStop()
            parallel.shouldBeAt(1)
            parallel.syncAt(2)
        }

        parallel.waitForAll()
        dispatch.stop()

        then:
        target1.receivedMessages == ['message1']
    }

    def 'stop fails when no targets are available to deliver queued messages'() {
        given:
        dispatch.dispatch('message1')

        when:
        dispatch.stop()

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot wait for messages to be dispatched, as there are no dispatch threads running.'
    }

    def 'stop fails when all targets have failed'() {
        when:
        dispatch.dispatchTo(target1)
        dispatch.dispatch('message1')
        dispatch.dispatch('message2')
        dispatch.stop()

        then:
        1 * target1.dispatch('message1') >> {
            RuntimeException failure = new RuntimeException()
            parallel.willFailWith(sameInstance(failure))
            throw failure
        }
        0 * _._
        IllegalStateException e = thrown()
        e.message == 'Cannot wait for messages to be dispatched, as there are no dispatch threads running.'
    }

    def 'cannot dispatch messages after stop'() {
        given:
        dispatch.stop()

        when:
        dispatch.dispatch('message')

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot dispatch message, as this message dispatch has been stopped. Message: message'
    }

    /**
     * We use a manual stub/mock here since {@link org.spockframework.mock.runtime.MockController#handle}
     * is synchronized and only allows that one mocked method is executed at a time. This would
     * make these tests block and fail.
     */
    public static class DispatchStub implements Dispatch<String> {
        private Map<String, Closure> behaviour

        private List<String> receivedMessages = []
        DispatchStub(Map<String, Closure> behaviour) {
            this.behaviour = behaviour
        }

        @Override
        void dispatch(String message) {
            receivedMessages.add(message)
            behaviour[message]?.call()
        }

        List<String> getReceivedMessages() {
            return receivedMessages
        }
    }
}
