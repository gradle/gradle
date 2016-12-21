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


package org.gradle.test.fixtures.server.http

import org.gradle.internal.time.TrueTimeProvider
import org.junit.rules.ExternalResource
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

public class BlockingHttpServer extends ExternalResource {
    private final Server server = new Server(0)
    private final HandlerCollection collection = new HandlerCollection()

    BlockingHttpServer() {
        def handlers = new HandlerCollection()
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                println("handling http request: $request.method $target")
            }
        })
        handlers.addHandler(collection)
        handlers.addHandler(new AbstractHandler() {
            void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
                if (request.handled) {
                    return
                }
                throw new AssertionError("Received unexpected ${request.method} request to ${target}.")
            }
        })
        server.setHandler(handlers)
    }

    /**
     * Returns the URI for the given call.
     */
    URI uri(String call) {
        return new URI("http", null, "localhost", getPort(), "/${call}", null, null)
    }

    /**
     * Returns a Gradle build script fragment that invokes the given call.
     */
    String callFromBuildScript(String call) {
        return "new URL('${uri(call)}').text"
    }

    /**
     * Expects the given calls to be made concurrently. Blocks each call until they have all been received.
     */
    void expectConcurrentExecution(String expectedCall, String... additionalExpectedCalls) {
        def handler = new CyclicBarrierRequestHandler((additionalExpectedCalls.toList() + expectedCall) as Set, {})
        collection.addHandler(handler)
    }

    /**
     * Expects the given call to be made.
     */
    void expectSerialExecution(String expectedCall) {
        def handler = new CyclicBarrierRequestHandler(expectedCall, {})
        collection.addHandler(handler)
    }

    void expectConcurrentExecution(Iterable<String> tasks, Runnable latch) {
        def handler = new CyclicBarrierRequestHandler(tasks as Set, latch)
        collection.addHandler(handler)
    }

    void start() {
        server.start()
    }

    void stop() {
        collection.handlers.each { handler ->
            handler.assertComplete()
        }
        server?.stop()
    }

    @Override
    protected void after() {
        stop()
    }

    int getPort() {
        def port = server.connectors[0].localPort
        if (port < 0) {
            throw new RuntimeException("""No port available for HTTP server. Still starting perhaps?
connector: ${server.connectors[0]}
connector state: ${server.connectors[0].dump()}
server state: ${server.dump()}
""")
        }
        return port
    }

    class CyclicBarrierRequestHandler extends AbstractHandler {
        final Lock lock = new ReentrantLock()
        final Condition condition = lock.newCondition()
        final List<String> received = []
        final Set<String> pending
        boolean shortCircuit
        private final Runnable latch

        CyclicBarrierRequestHandler(Set calls, Runnable latch) {
            this.latch = latch
            pending = calls
        }

        CyclicBarrierRequestHandler(String call, Runnable latch) {
            this.latch = latch
            pending = [call] as Set
        }

        void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
            if (request.handled) {
                return
            }

            Date expiry = new Date(new TrueTimeProvider().getCurrentTime() + 30000)
            lock.lock()
            try {
                if (shortCircuit) {
                    request.handled = true
                    return
                }
                def path = target.replaceFirst('/', '')
                if (!pending.remove(path)) {
                    if (!pending.empty) {
                        shortCircuit = true
                        condition.signalAll()
                        throw new AssertionError("Unexpected call to '$target' received. Waiting for $pending, already received $received.")
                    }
                    // barrier open, let it travel on
                    return
                }
                latch.run()
                received.add(path)
                condition.signalAll()
                while (!pending.empty && !shortCircuit) {
                    if (!condition.awaitUntil(expiry)) {
                        throw new AssertionError("Timeout waiting for all concurrent requests. Waiting for $pending, received $received.")
                    }
                }
                if (!shortCircuit) {
                    response.addHeader("RESPONSE", "target: done")
                }
            } finally {
                lock.unlock()
            }

            request.handled = true
        }

        void assertComplete() {
            if (!pending.empty) {
                throw new AssertionError("BlockingHttpServer: did not receive expected concurrent requests. Waiting for $pending, received $received")
            }
        }
    }
}
