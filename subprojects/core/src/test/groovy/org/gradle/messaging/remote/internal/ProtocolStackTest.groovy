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

import java.util.concurrent.TimeUnit
import org.gradle.messaging.dispatch.Dispatch
import org.gradle.messaging.dispatch.DispatchFailureHandler
import org.gradle.util.ConcurrentSpecification

class ProtocolStackTest extends ConcurrentSpecification {
    final Protocol<String> top = Mock()
    final Protocol<String> bottom = Mock()
    final Dispatch<String> outgoing = Mock()
    final Dispatch<String> incoming = Mock()
    final DispatchFailureHandler<String> outgoingFailureHandler = Mock()
    final DispatchFailureHandler<String> incomingFailureHandler = Mock()
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
        _ * outgoingFailureHandler.dispatchFailed(!null, !null) >> { throw it[1] }
        _ * incomingFailureHandler.dispatchFailed(!null, !null) >> { throw it[1] }

        stack = new ProtocolStack<String>(executor, outgoingFailureHandler, incomingFailureHandler, top, bottom)
        stack.bottom.dispatchTo(outgoing)
    }

    def cleanup() {
        stack?.stop()
    }

    def "starts protocol on construction"() {
        Protocol<String> protocol = Mock()
        def started = startsAsyncAction()

        when:
        def stack
        started.started {
            stack = new ProtocolStack<String>(executor, outgoingFailureHandler, incomingFailureHandler, protocol)
        }

        then:
        1 * protocol.start(!null) >> { started.done() }

        cleanup:
        stack?.stop()
    }

    def "top protocol can dispatch incoming message during start"() {
        Protocol<String> protocol = Mock()
        def dispatched = startsAsyncAction()

        when:
        def stack
        dispatched.started {
            stack = new ProtocolStack<String>(executor, outgoingFailureHandler, incomingFailureHandler, protocol)
            stack.top.dispatchTo(incoming)
        }

        then:
        1 * protocol.start(!null) >> { it[0].dispatchIncoming("message") }
        1 * incoming.dispatch("message") >> { dispatched.done() }

        cleanup:
        stack?.stop()
    }

    def "outgoing message is dispatched to top protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.top.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { dispatched.done() }
    }

    def "incoming message is dispatched to bottom protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.bottom.dispatch("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { dispatched.done() }
    }

    def "incoming message dispatched by protocol is dispatch to next higher protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.bottom.dispatch("message")
        }

        then:
        1 * bottom.handleIncoming("message") >> { bottomContext.dispatchIncoming("transformed") }
        1 * top.handleIncoming("transformed") >> { dispatched.done() }
    }

    def "outgoing message dispatch by protocol is dispatched to next lower protocol"() {
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.top.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("transformed") }
        1 * bottom.handleOutgoing("transformed") >> { dispatched.done() }
    }

    def "incoming message dispatched by top protocol is dispatch to a handler"() {
        stack.top.dispatchTo(incoming)
        def dispatched = startsAsyncAction()

        when:
        dispatched.started {
            stack.bottom.dispatch("message")
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
            stack.top.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("outgoing1") }
        1 * bottom.handleOutgoing("outgoing1") >> { bottomContext.dispatchOutgoing("outgoing2") }
        1 * outgoing.dispatch("outgoing2") >> { dispatched.done() }
    }

    def "protocol callback after timeout"() {
        Runnable callback = Mock()
        def calledBack = startsAsyncAction()

        when:
        calledBack.started {
            stack.top.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> {
            topContext.callbackLater(500, TimeUnit.MILLISECONDS, callback)
        }
        1 * callback.run() >> { calledBack.done() }
    }

    def "protocol callback is not called after it is cancelled"() {
        Runnable callback = Mock()
        def calledBack = startsAsyncAction()

        when:
        calledBack.started {
            stack.top.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> {
            topContext.callbackLater(200, TimeUnit.MILLISECONDS, callback).cancel()
            Thread.sleep(500)
            calledBack.done()
        }
        0 * callback._
    }

    def "protocol callback with long delay is not called after protocol is stopped"() {
        Runnable callback = Mock()
        def callbackRegistered = startsAsyncAction()

        when:
        callbackRegistered.started {
            stack.top.dispatch("message")
        }
        stack.stop()

        then:
        1 * top.handleOutgoing("message") >> {
            topContext.callbackLater(5, TimeUnit.SECONDS, callback)
            callbackRegistered.done()
        }
        0 * callback._
    }

    def "protocol callback with short delay is not called after protocol is stopped"() {
        Runnable callback = Mock()
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            stack.stop()
        }

        then:
        1 * top.stopRequested() >> {
            topContext.callbackLater(0, TimeUnit.MILLISECONDS, callback)
            stopped.done()
        }
        0 * callback._
    }

    def "notifies failure handler when protocol fails to handle outgoing"() {
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            stack.top.dispatch("message")
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
            stack.bottom.dispatch("message")
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
            stack.top.dispatch("message")
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("message") }
        1 * bottom.handleOutgoing("message") >> { bottomContext.dispatchOutgoing("message") }
        1 * outgoing.dispatch("message") >> { throw failure }
        1 * outgoingFailureHandler.dispatchFailed("message", failure) >> { notified.done() }
    }

    def "notifies failure handler when incoming handler throws exception"() {
        stack.top.dispatchTo(incoming)
        def failure = new RuntimeException()
        def notified = startsAsyncAction()

        when:
        notified.started {
            stack.bottom.dispatch("message")
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

    def "stop blocks until protocols have stopped"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            stack.stop()
        }

        then:
        1 * top.stopRequested() >> {
            topContext.stopLater()
            topContext.dispatchOutgoing("stopping")
        }
        1 * bottom.handleOutgoing("stopping") >> { bottomContext.dispatchIncoming("ok") }
        1 * top.handleIncoming("ok") >> { topContext.stopped() }
        1 * bottom.stopRequested() >> {
            stopped.done()
        }
    }

    def "stop blocks until all incoming messages handled"() {
        stack.top.dispatchTo(incoming)
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            stack.bottom.dispatch("message")
            stack.stop()
        }

        then:
        1 * bottom.handleIncoming("message") >> { bottomContext.dispatchIncoming("message") }
        1 * top.handleIncoming("message") >> { topContext.dispatchIncoming("message") }
        1 * incoming.dispatch("message") >> { stopped.done() }
    }

    def "stop blocks until all outgoing messages dispatched"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            stack.top.dispatch("message")
            stack.stop()
        }

        then:
        1 * top.handleOutgoing("message") >> { topContext.dispatchOutgoing("message") }
        1 * bottom.handleOutgoing("message") >> { bottomContext.dispatchOutgoing("message") }
        1 * outgoing.dispatch("message") >> { stopped.done() }
    }

    def "protocols can dispatch outgoing messages on stop"() {
        def stopped = waitsForAsyncActionToComplete()

        when:
        stopped.start {
            stack.stop()
        }

        then:
        1 * top.stopRequested() >> { topContext.dispatchOutgoing("top stopped") }
        1 * bottom.handleOutgoing("top stopped") >> { bottomContext.dispatchOutgoing("top stopped") }
        1 * bottom.stopRequested() >> { bottomContext.dispatchOutgoing("bottom stopped") }

        and:
        1 * outgoing.dispatch("top stopped")

        and:
        1 * outgoing.dispatch("bottom stopped") >> { stopped.done() }
    }

}
