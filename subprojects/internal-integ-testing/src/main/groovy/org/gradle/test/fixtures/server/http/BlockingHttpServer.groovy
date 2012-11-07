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



package org.gradle.test.fixtures.server.http;


import org.junit.rules.ExternalResource
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

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

    void expectConcurrentExecution(String... expectedCalls) {
        def handler = new CyclicBarrierRequestHandler(expectedCalls)
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

        CyclicBarrierRequestHandler(String... calls) {
            pending = calls as Set
        }

        void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
            if (request.handled) {
                return
            }

            Date expiry = new Date(System.currentTimeMillis() + 30000)
            lock.lock()
            try {
                def path = target.replaceFirst('/', '')
                if (!pending.remove(path)) {
                    // Unexpected call - let it travel on
                    return
                }
                received.add(path)
                condition.signalAll()
                while (!pending.empty) {
                    if (!condition.awaitUntil(expiry)) {
                        throw new AssertionError("Timeout waiting for all concurrent requests. Waiting for $pending, received $received.")
                    }
                }
            } finally {
                lock.unlock()
            }

            response.addHeader("RESPONSE", "target: done")
            request.handled = true
        }

        void assertComplete() {
            if (!pending.empty) {
                throw new AssertionError("BlockingHttpServer: did not receive expected concurrent requests. Waiting for $pending, received $received")
            }
        }
    }
}
