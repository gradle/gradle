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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An HTTP server that allows a test to synchronize and make assertions about concurrent activities that happen in another process.
 * For example, can be used to that certain tasks do or do not execute in parallel.
 */
public class BlockingHttpServer extends ExternalResource {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private final Lock lock = new ReentrantLock();
    private final HttpServer server;
    private final ChainingHttpHandler handler;
    private final int timeoutMs;
    private final int serverId;

    public BlockingHttpServer() throws IOException {
        this(30000);
    }

    public BlockingHttpServer(int timeoutMs) throws IOException {
        // Use an OS selected port
        server = HttpServer.create(new InetSocketAddress(0), 10);
        server.setExecutor(Executors.newCachedThreadPool());
        serverId = COUNTER.incrementAndGet();
        handler = new ChainingHttpHandler(lock, COUNTER);
        server.createContext("/", handler);
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns the URI for this server.
     */
    public URI getUri() {
        try {
            return new URI("http", null, "localhost", getPort(), "/", null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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
        resourceHandlers.add(resourceHandler(expectedCall));
        for (String call : additionalExpectedCalls) {
            resourceHandlers.add(resourceHandler(call));
        }
        handler.addHandler(new CyclicBarrierRequestHandler(lock, timeoutMs, resourceHandlers));
    }

    /**
     * Expects the given calls to be made concurrently. Blocks each call until they have all been received.
     */
    public void expectConcurrentExecution(Collection<String> expectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        for (String call : expectedCalls) {
            resourceHandlers.add(resourceHandler(call));
        }
        handler.addHandler(new CyclicBarrierRequestHandler(lock, timeoutMs, resourceHandlers));
    }

    /**
     * Expects the given calls to be made concurrently. Blocks each call until they have all been received.
     */
    public void expectConcurrentExecutionTo(Collection<? extends Resource> expectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        for (Resource call : expectedCalls) {
            resourceHandlers.add((ResourceHandler) call);
        }
        handler.addHandler(new CyclicBarrierRequestHandler(lock, timeoutMs, resourceHandlers));
    }

    /**
     * Expect a GET request to the given path, and return the contents of the given file.
     */
    public Resource file(String path, File file) {
        return new FileResourceHandler(path, file);
    }

    /**
     * Expect a GET request to the given path.
     */
    public Resource resource(String path) {
        return resourceHandler(path);
    }

    private SimpleResourceHandler resourceHandler(String path) {
        return new SimpleResourceHandler(path);
    }

    /**
     * Expects exactly the given number of calls to be made concurrently from any combination of the expected calls. Blocks each call until they are explicitly released.
     * Is not considered "complete" until all expected calls have been received.
     */
    public BlockingHandler blockOnConcurrentExecutionAnyOf(int concurrent, String... expectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        for (String call : expectedCalls) {
            resourceHandlers.add(resourceHandler(call));
        }
        CyclicBarrierAnyOfRequestHandler requestHandler = new CyclicBarrierAnyOfRequestHandler(lock, serverId, timeoutMs, concurrent, resourceHandlers);
        handler.addHandler(requestHandler);
        return requestHandler;
    }

    /**
     * Expects exactly the given number of calls to be made concurrently from any combination of the expected calls. Blocks each call until they are explicitly released.
     * Is not considered "complete" until all expected calls have been received.
     */
    public BlockingHandler blockOnConcurrentExecutionAnyOfToResources(int concurrent, Collection<? extends Resource> expectedCalls) {
        List<ResourceHandler> resourceHandlers = new ArrayList<ResourceHandler>();
        for (Resource call : expectedCalls) {
            resourceHandlers.add((ResourceHandler) call);
        }
        CyclicBarrierAnyOfRequestHandler requestHandler = new CyclicBarrierAnyOfRequestHandler(lock, serverId, timeoutMs, concurrent, resourceHandlers);
        handler.addHandler(requestHandler);
        return requestHandler;
    }

    /**
     * Expects the given call to be made.
     */
    public void expectSerialExecution(String expectedCall) {
        handler.addHandler(new CyclicBarrierRequestHandler(lock, timeoutMs, Collections.singleton(resourceHandler(expectedCall))));
    }

    /**
     * Expects the given call to be made.
     */
    public void expectSerialExecution(Resource expectedCall) {
        handler.addHandler(new CyclicBarrierRequestHandler(lock, timeoutMs, Collections.singleton((ResourceHandler) expectedCall)));
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

    /**
     * For testing this fixture only.
     */
    void waitForRequests(int requestCount) {
        handler.waitForRequests(requestCount);
    }

    @Override
    protected void after() {
        stop();
    }

    private int getPort() {
        return server.getAddress().getPort();
    }

    /**
     * Represents some HTTP resource.
     */
    public interface Resource {
    }

    /**
     * Allows the test to synchronise with and unblock requests.
     */
    public interface BlockingHandler {
        /**
         * Releases the given number of blocked requests. Fails when fewer than the given number of requests are waiting to be released.
         */
        void release(int count);

        /**
         * Releases the given request. Fails when the given request is not waiting to be released.
         */
        void release(String path);

        /**
         * Releases all requests. Fails when there are requests yet to be received.
         */
        void releaseAll();

        /**
         * Waits for the expected number of concurrent requests to be received.
         */
        void waitForAllPendingCalls();
    }

}
