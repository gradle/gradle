/*
 * Copyright 2017 the original author or authors.
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

import com.sun.net.httpserver.HttpServer;
import org.junit.rules.ExternalResource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * An HTTP server that allows a test to synchronize and make assertions about concurrent activities that happen in another process.
 * For example, can be used to that certain tasks do or do not execute in parallel.
 */
public class BlockingHttpServer extends ExternalResource {
    private final HttpServer server;
    private final ChainingHttpHandler handler;

    public BlockingHttpServer() throws IOException {
        // Use an OS selected port
        server = HttpServer.create(new InetSocketAddress(0), 10);
        server.setExecutor(Executors.newCachedThreadPool());
        handler = new ChainingHttpHandler();
        server.createContext("/", handler);
    }

    /**
     * Returns the URI for the given call.
     */
    public URI uri(String call) {
        try {
            return new URI("http", null, "localhost", getPort(), "/" + call, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a Gradle build script fragment that invokes the given call.
     */
    public String callFromBuildScript(String call) {
        return "new URL('" + uri(call) + "').text";
    }

    /**
     * Expects the given calls to be made concurrently. Blocks each call until they have all been received.
     */
    public void expectConcurrentExecution(String expectedCall, String... additionalExpectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        resourceHandlers.add(new SimpleResourceHandler(expectedCall));
        for (String call : additionalExpectedCalls) {
            resourceHandlers.add(new SimpleResourceHandler(call));
        }
        handler.addHandler(new CyclicBarrierRequestHandler(resourceHandlers));
    }

    /**
     * Expects the given calls to be made concurrently. Blocks each call until they have all been received.
     */
    public void expectConcurrentExecution(Collection<String> expectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        for (String call : expectedCalls) {
            resourceHandlers.add(new SimpleResourceHandler(call));
        }
        handler.addHandler(new CyclicBarrierRequestHandler(resourceHandlers));
    }

    /**
     * Expects exactly the given number of calls to be made concurrently from any combination of the expected calls. Blocks each call until they are explicitly released.
     * Is not considered "complete" until all expected calls have been received.
     */
    public BlockingHandler blockOnConcurrentExecutionAnyOf(int concurrent, String... expectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        for (String call : expectedCalls) {
            resourceHandlers.add(new SimpleResourceHandler(call));
        }
        CyclicBarrierAnyOfRequestHandler requestHandler = new CyclicBarrierAnyOfRequestHandler(concurrent, resourceHandlers);
        handler.addHandler(requestHandler);
        return requestHandler;
    }

    /**
     * Expects the given call to be made.
     */
    public void expectSerialExecution(String expectedCall) {
        handler.addHandler(new CyclicBarrierRequestHandler(Collections.singleton(new SimpleResourceHandler(expectedCall))));
    }

    public void start() {
        server.start();
    }

    public void stop() {
        handler.assertComplete();
        // Stop is very slow, clean it up later
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                server.stop(10);
            }
        });
    }

    @Override
    protected void after() {
        stop();
    }

    private int getPort() {
        return server.getAddress().getPort();
    }

    interface BlockingHandler {
        void release(int count);

        void waitForAllPendingCalls(int timeoutSeconds);
    }
}
