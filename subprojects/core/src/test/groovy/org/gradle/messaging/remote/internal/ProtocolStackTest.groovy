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
package org.gradle.messaging.remote.internal

import org.gradle.messaging.dispatch.Dispatch
import org.gradle.util.ConcurrentSpecification
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ArrayBlockingQueue
import org.gradle.messaging.dispatch.ReceiveFailureHandler
import org.gradle.messaging.dispatch.DispatchFailureHandler
import spock.lang.Ignore
import org.gradle.messaging.dispatch.Receive

class ProtocolStackTest extends ConcurrentSpecification {
    final Protocol<String> top = Mock()
    final Protocol<String> bottom = Mock()
    final Dispatch<String> outgoing = Mock()
    final ReceiveFailureHandler receiveFailureHandler = Mock()
    final DispatchFailureHandler<String> outgoingFailureHandler = Mock()
    final DispatchFailureHandler<String> incomingFailureHandler = Mock()
    final TestReceive receive = new TestReceive()
    ProtocolContext<String> topContext
    ProtocolContext<String> bottomContext
    ProtocolStack<String> stack

    def setup() {
        _ * top.start(!null) >> {
            topContext = it[0]
        }
        _ * bottom.start(!null) >> {
            bottomContext = it[0]
        }
        stack = new ProtocolStack<String>(outgoing, receive, executor, receiveFailureHandler, outgoingFailureHandler, incomingFailureHandler, top, bottom)
    }

    def cleanup() {
        receive?.stop()
        stack?.stop()
    }

    def "starts protocol on construction"() {
        Protocol<String> protocol = Mock()

        when:
        def stack = new ProtocolStack<String>(outgoing, receive, executor, receiveFailureHandler, outgoingFailureHandler, incomingFailureHandler, protocol)

        then:
        1 * protocol.start(!null)

        cleanup:
        receive?.stop()
        stack?.stop()
    }

    def "outgoing message is dispatched to top protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { dispatched.done() }
    }

    def "incoming message is dispatched to bottom protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            receive.receive("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { dispatched.done() }
    }

    def "incoming message dispatched by protocol is dispatch to next higher protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            receive.receive("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { bottomContext.dispatchIncoming("transformed") }
        1 * top.handleIncoming("transformed") >> { dispatched.done() }
    }

    def "outgoing message dispatch by protocol is dispatched to next lower protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("transformed") }
        1 * bottom.handleOutgoing("transformed") >> { dispatched.done() }
    }

    def "incoming message dispatched by top protocol is dispatch to a handler"() {
        Dispatch<String> incoming = Mock()
        stack.receiveOn(incoming)
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            receive.receive("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { bottomContext.dispatchIncoming("incoming1") }
        1 * top.handleIncoming("incoming1") >> { topContext.dispatchIncoming("incoming2") }
        1 * incoming.dispatch("incoming2") >> { dispatched.done() }
    }

    def "outgoing message dispatch by bottom protocol is dispatched to connection"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("outgoing1") }
        1 * bottom.handleOutgoing("outgoing1") >> { bottomContext.dispatchOutgoing("outgoing2") }
        1 * outgoing.dispatch("outgoing2") >> { dispatched.done() }
    }

    @Ignore
    def "loopback message is dispatched to protocol after timeout"() {
        expect: false
    }

    def "notifies failure handler on receive failure"() {
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            receive.receive(failure)
        }

        then:
        1 * receiveFailureHandler.receiveFailed(failure) >> { notified.done() }
    }

    def "notifies failure handler when protocol fails to handle outgoing"() {
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            stack.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { throw failure }
        1 * outgoingFailureHandler.dispatchFailed("message", failure) >> { notified.done() }
    }

    def "notifies failure handler when protocol fails to handle incoming"() {
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            receive.receive("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { throw failure }
        1 * incomingFailureHandler.dispatchFailed("message", failure) >> { notified.done() }
    }

    def "notifies failure handler when connection fails to dispatch outgoing"() {
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            stack.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("message") }
        1 * bottom.handleOutgoing("message") >> { bottomContext.dispatchOutgoing("message") }
        1 * outgoing.dispatch("message") >> { throw failure }
        1 * outgoingFailureHandler.dispatchFailed("message", failure) >> { notified.done() }
    }

    def "notifies failure handler when incoming handler throws exception"() {
        Dispatch<String> incoming = Mock()
        stack.receiveOn(incoming)
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            receive.receive("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { bottomContext.dispatchIncoming("message") }
        1 * top.handleIncoming("message") >> { topContext.dispatchIncoming("message") }
        1 * incoming.dispatch("message") >> { throw failure }
        1 * incomingFailureHandler.dispatchFailed("message", failure) >> { notified.done() }
    }

    def "requests protocols stop from top to bottom"() {
        def stopRequested = startsAsyncAction()

        when:
        stopRequested.started {
            stack.requestStop()
        }

        then:
        1 * top.stopRequested()
        1 * bottom.stopRequested() >> { stopRequested.done() }
    }

    def "stops blocks until protocols have stopped"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            receive.stop()
            stack.stop()
        }

        then:
        1 * top.stopRequested() >> {
            topContext.stopLater(); topContext.stopped()
        }
        1 * bottom.stopRequested() >> {
            stopped.done()
        }
    }

    def "stops blocks until all incoming messages handled"() {
        Dispatch<String> incoming = Mock()
        stack.receiveOn(incoming)
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            receive.receive("message")
            stack.stop()
        }

        then:
        1 * bottom.handleIncoming("message") >> { bottomContext.dispatchIncoming("message") }
        1 * top.handleIncoming("message") >> { topContext.dispatchIncoming("message") }
        1 * incoming.dispatch("message") >> { stopped.done(); receive.stop() }
    }

    def "stop blocks until all outgoing messages dispatched"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            stack.dispatch("message")
            receive.stop()
            stack.stop()
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("message") }
        1 * bottom.handleOutgoing("message") >> { bottomContext.dispatchOutgoing("message") }
        1 * outgoing.dispatch("message") >> { stopped.done() }
    }
}

class TestReceive implements Receive<String> {
    final BlockingQueue<Object> incoming = new ArrayBlockingQueue<Object>(5)
    final Object endIncoming = new Object()

    String receive() {
        def result = incoming.take()
        if (result == endIncoming) {
            incoming.put(endIncoming)
            return null;
        }
        if (result instanceof Throwable) {
            Throwable throwable = (Throwable) result;
            throw throwable
        }
        return result
    }

    void receive(String message) {
        incoming.put(message)
    }

    void receive(Throwable failure) {
        incoming.put(failure)
    }

    void requestStop() {
        incoming.put(endIncoming)
    }

    void stop() {
        requestStop()
    }
}
