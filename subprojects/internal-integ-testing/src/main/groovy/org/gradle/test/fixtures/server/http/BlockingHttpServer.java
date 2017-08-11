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

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
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
import java.util.concurrent.ExecutorService;
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
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private final Lock lock = new ReentrantLock();
    private final HttpServer server;
    private HttpContext context;
    private final ChainingHttpHandler handler;
    private final int timeoutMs;
    private final int serverId;
    private boolean running;

    public BlockingHttpServer() throws IOException {
        this(120000);
    }

    public BlockingHttpServer(int timeoutMs) throws IOException {
        // Use an OS selected port
        server = HttpServer.create(new InetSocketAddress(0), 10);
        server.setExecutor(EXECUTOR_SERVICE);
        serverId = COUNTER.incrementAndGet();
        handler = new ChainingHttpHandler(lock, COUNTER, new MustBeRunning());
        context = server.createContext("/", handler);
        this.timeoutMs = timeoutMs;
    }

    /**
     * Returns the URI for this server.
     */
    public URI getUri() {
        try {
            return new URI("http://localhost:" + getPort());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the URI for the given resource.
     */
    public URI uri(String resource) {
        try {
            return new URI("http", null, "localhost", getPort(), "/" + resource, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns Java statements to get the given resource.
     */
    public String callFromBuild(String resource) {
        URI uri = uri(resource);
        return "System.out.println(\"calling " + uri + "\"); try { new java.net.URL(\"" + uri + "\").openConnection().getContentLength(); } catch(Exception e) { throw new RuntimeException(e); }; System.out.println(\"[G] response received\");";
    }

    /**
     * Returns Java statements to get the given resource, using the given expression to calculate the resource to get.
     */
    public String callFromBuildUsingExpression(String expression) {
        String uriExpression = "\"" + getUri() + "/\" + " + expression;
        return "System.out.println(\"calling \" + " + uriExpression + "); try { new java.net.URL(" + uriExpression + ").openConnection().getContentLength(); } catch(Exception e) { throw new RuntimeException(e); }; System.out.println(\"[G] response received\");";
    }

    public void withBasicAuthentication(final String username, final String password) {
        context.setAuthenticator(new BasicAuthenticator("get") {
            @Override
            public boolean checkCredentials(String u, String pwd) {
                return u.equals(username) && password.equals(pwd);
            }
        });
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public void expectConcurrent(String... expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<ResourceExpectation>();
        for (String call : expectedRequests) {
            expectations.add(new SendFixedContent(call));
        }
        addNonBlockingHandler(expectations);
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public void expectConcurrent(Collection<String> expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<ResourceExpectation>();
        for (String call : expectedRequests) {
            expectations.add(new SendFixedContent(call));
        }
        addNonBlockingHandler(expectations);
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public void expectConcurrent(ExpectedRequest... expectedCalls) {
        List<ResourceExpectation> expectations = new ArrayList<ResourceExpectation>();
        for (ExpectedRequest call : expectedCalls) {
            expectations.add((ResourceExpectation) call);
        }
        addNonBlockingHandler(expectations);
    }

    private void addNonBlockingHandler(final Collection<? extends ResourceExpectation> expectations) {
        handler.addHandler(new ChainingHttpHandler.HandlerFactory() {
            @Override
            public TrackingHttpHandler create(WaitPrecondition previous) {
                return new CyclicBarrierRequestHandler(lock, timeoutMs, previous, expectations);
            }
        });
    }

    /**
     * Expect a GET request to the given path, and return the contents of the given file.
     */
    public ExpectedRequest file(String path, File file) {
        return new SendFileContent(path, file);
    }

    /**
     * Expect a GET request to the given path, and return some arbitrary content.
     */
    public ExpectedRequest resource(String path) {
        return new SendFixedContent(path);
    }

    /**
     * Expect a GET request to the given path, and return a 404 response.
     */
    public ExpectedRequest missing(String path) {
        return new ExpectGetMissing(path);
    }

    /**
     * Expect a HEAD request to the given path.
     */
    public ExpectedRequest head(String path) {
        return new ExpectHead(path);
    }

    /**
     * Expect a GET request to the given path, and return the given content (UTF-8 encoded)
     */
    public ExpectedRequest resource(String path, String content) {
        return new SendFixedContent(path, content);
    }

    /**
     * Expect a PUT request to the given path, discard the request body
     */
    public ExpectedRequest put(String path) {
        return new ExpectPut(path);
    }

    /**
     * Expect a GET request to the given path. Return 1K of the given content then block waiting for {@link BlockingRequest#release()} before returning the remainder
     */
    public BlockingRequest sendSomeAndBlock(String path, byte[] content) {
        if (content.length < 1024) {
            throw new IllegalArgumentException("Content is too short.");
        }
        return new SendPartialResponseThenBlock(lock, timeoutMs, path, content);
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received and released using one of the methods on {@link BlockingHandler}.
     */
    public BlockingHandler expectConcurrentAndBlock(String... expectedCalls) {
        return expectConcurrentAndBlock(expectedCalls.length, expectedCalls);
    }

    /**
     * Expects exactly the given number of calls to be made concurrently from any combination of the expected calls. Blocks each call until they are explicitly released.
     * Is not considered "complete" until all expected calls have been received.
     */
    public BlockingHandler expectConcurrentAndBlock(int concurrent, String... expectedCalls) {
        List<ResourceExpectation> expectations = new ArrayList<ResourceExpectation>();
        for (String call : expectedCalls) {
            expectations.add(new SendFixedContent(call));
        }
        return addBlockingHandler(concurrent, expectations);
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received and released using one of the methods on {@link BlockingHandler}.
     */
    public BlockingHandler expectConcurrentAndBlock(ExpectedRequest... expectedRequests) {
        return expectConcurrentAndBlock(expectedRequests.length, expectedRequests);
    }

    /**
     * Expects exactly the given number of calls to be made concurrently from any combination of the expected calls. Blocks each call until they are explicitly released.
     * Is not considered "complete" until all expected calls have been received.
     */
    public BlockingHandler expectConcurrentAndBlock(int concurrent, ExpectedRequest... expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<ResourceExpectation>();
        for (ExpectedRequest request : expectedRequests) {
            expectations.add((ResourceExpectation) request);
        }
        return addBlockingHandler(concurrent, expectations);
    }

    private BlockingHandler addBlockingHandler(final int concurrent, final Collection<? extends ResourceExpectation> expectations) {
        return handler.addHandler(new ChainingHttpHandler.HandlerFactory<CyclicBarrierAnyOfRequestHandler>() {
            @Override
            public CyclicBarrierAnyOfRequestHandler create(WaitPrecondition previous) {
                return new CyclicBarrierAnyOfRequestHandler(lock, serverId, timeoutMs, concurrent, previous, expectations);
            }
        });
    }

    /**
     * Expects the given request to be made. Releases the request as soon as it is received.
     */
    public void expect(String expectedCall) {
        addNonBlockingHandler(Collections.singleton(new SendFixedContent(expectedCall)));
    }

    /**
     * Expects the given request to be made. Blocks until the request is explicitly released using one of the methods on {@link BlockingHandler}.
     */
    public BlockingHandler expectAndBlock(String expectedCall) {
        return addBlockingHandler(1, Collections.singleton(new SendFixedContent(expectedCall)));
    }

    /**
     * Expects the given request to be made. Releases the request as soon as it is received.
     */
    public void expect(ExpectedRequest expectedRequest) {
        addNonBlockingHandler(Collections.singleton((ResourceExpectation) expectedRequest));
    }

    /**
     * Expects the given request to be made. Blocks until the request is explicitly released using one of the methods on {@link BlockingHandler}.
     */
    public BlockingHandler expectAndBlock(ExpectedRequest expectedRequest) {
        return addBlockingHandler(1, Collections.singleton((ResourceExpectation) expectedRequest));
    }

    public void start() {
        server.start();
        running = true;
    }

    public void stop() {
        handler.assertComplete();
        running = false;
        // Stop is very slow, clean it up later
        EXECUTOR_SERVICE.execute(new Runnable() {
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

    public int getPort() {
        if (!running) {
            throw new IllegalStateException("Cannot get HTTP port as server is not running.");
        }
        return server.getAddress().getPort();
    }

    /**
     * Represents some HTTP request expectation.
     */
    public interface ExpectedRequest {
    }

    /**
     * Allows the test to synchronise with and unblock a single request.
     */
    public interface BlockingRequest extends ExpectedRequest {
        /**
         * Waits for the request to be received and blocked.
         */
        void waitUntilBlocked();

        /**
         * Unblock the request.
         */
        void release();
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

    private class MustBeRunning implements WaitPrecondition {
        @Override
        public void assertCanWait() throws IllegalStateException {
            lock.lock();
            try {
                if (!running) {
                    throw new IllegalStateException("Cannot wait as the server is not running.");
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
