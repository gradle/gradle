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



package org.gradle.integtests.fixtures;

import org.junit.rules.ExternalResource
import org.mortbay.jetty.Server
import org.mortbay.jetty.handler.AbstractHandler
import org.mortbay.jetty.handler.HandlerCollection

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
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
        def expectedCalls = []
        def collectedCalls = []
        def cyclicBarrier
        def complete

        CyclicBarrierRequestHandler(String... calls) {
            this.expectedCalls.addAll(calls)
            cyclicBarrier = new CyclicBarrier(expectedCalls.size(), {
                assert collectedCalls.toSet() == expectedCalls.toSet()
                complete = true
            } as Runnable)
        }

        void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) {
            if (request.handled || complete) {
                return
            }
            recordPath(target)
            cyclicBarrier.await(10, TimeUnit.SECONDS)
            response.addHeader("RESPONSE", "target: done")
            request.handled = true
        }

        private synchronized void recordPath(def target) {
            def path = target.replaceFirst('/', '')
            collectedCalls.add path
        }

        void assertComplete() {
            if (!complete) {
                throw new AssertionError("BlockingHttpServer: did not receive simultaneous calls $expectedCalls")
            }
        }
    }
}
