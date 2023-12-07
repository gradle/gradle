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
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.gradle.api.Action;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.test.fixtures.ResettableExpectations;
import org.hamcrest.Matcher;
import org.junit.rules.ExternalResource;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An HTTP server that allows a test to synchronize and make assertions about concurrent activities that happen in another process.
 * For example, can be used to that certain tasks do or do not execute in parallel.
 */
public class BlockingHttpServer extends ExternalResource implements ResettableExpectations {
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private final Lock lock = new ReentrantLock();
    protected final HttpServer server;
    private final HttpContext context;
    private final ChainingHttpHandler handler;
    private final Duration timeout;
    private final int serverId;
    private final Scheme scheme;
    private boolean running;
    private int clientVarCounter;
    private String hostAlias;

    public BlockingHttpServer() throws IOException {
        this(120_000);
    }

    public BlockingHttpServer(int timeoutMs) throws IOException {
        // Use an OS selected port
        this(HttpServer.create(new InetSocketAddress(0), 10), timeoutMs, Scheme.HTTP);
    }

    public void setHostAlias(String hostAlias) {
        this.hostAlias = hostAlias;
    }

    protected BlockingHttpServer(HttpServer server, int timeoutMs, Scheme scheme) {
        this.server = server;
        this.server.setExecutor(EXECUTOR_SERVICE);
        this.serverId = COUNTER.incrementAndGet();
        this.handler = new ChainingHttpHandler(lock, timeoutMs, COUNTER, new MustBeRunning());
        this.context = server.createContext("/", handler);
        this.timeout = Duration.ofMillis(timeoutMs);
        this.scheme = scheme;
    }

    /**
     * Returns the URI for this server.
     */
    public URI getUri() {
        try {
            String host = hostAlias;
            if (host == null) {
                host = scheme.host;
            }
            return new URI(scheme.scheme + "://" + host + ":" + getPort());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the URI for the given resource.
     */
    public URI uri(String resource) {
        try {
            return new URI(scheme.scheme, null, scheme.host, getPort(), "/" + resource, null, null);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns Java statements to get the given resource.
     */
    public String callFromBuild(String resource) {
        return callFromBuildUsingExpression("\"" + resource + "\"");
    }

    /**
     * Returns Java statements to get the given resource, using the given expression to calculate the resource to get.
     */
    public String callFromBuildUsingExpression(String expression) {
        String uriExpression = "\"" + getUri() + "/\" + " + expression;
        int count = clientVarCounter++;
        String connectionVar = "connection" + count;
        String urlVar = "url" + count;
        String streamVar = "inputStream" + count;
        StringWriter result = new StringWriter();
        PrintWriter writer = new PrintWriter(result);
        writer.println("String " + urlVar + " = " + uriExpression + ";");
        writer.println("System.out.println(\"[G] calling \" + " + urlVar + ");");
        writer.println("try {");
        writer.println("  java.net.URLConnection " + connectionVar + " = new java.net.URL(" + urlVar + ").openConnection();");
        writer.println("  " + connectionVar + ".setReadTimeout(0);"); // to avoid silent retry
        writer.println("  " + connectionVar + ".connect();");
        writer.println("  java.io.InputStream " + streamVar + " = " + connectionVar + ".getInputStream();");
        writer.println("  try {");
        writer.println("    while (" + streamVar + ".read() >= 0) {}"); // read entire response
        writer.println("  } finally {");
        writer.println("    " + streamVar + ".close();");
        writer.println("  }");
        writer.println("} catch(Exception e) {");
        writer.println("  System.out.println(\"[G] error response received for \" + " + urlVar + ");");
        writer.println("  throw new RuntimeException(\"Received error response from \" + " + urlVar + ", e);");
        writer.println("};");
        writer.println("System.out.println(\"[G] response received for \" + " + urlVar + ");");
        return result.toString();
    }

    public String callFromTaskAction(String resource) {
        return "getServices().get(" + WorkerLeaseService.class.getCanonicalName() + ".class).blocking(new Runnable() { void run() { " + callFromBuild(resource) + " } });";
    }

    /**
     * Expects that all requests use the basic authentication with the given credentials.
     */
    public void withBasicAuthentication(final String username, final String password) {
        context.setAuthenticator(new BasicAuthenticator("get") {
            @Override
            public boolean checkCredentials(String suppliedUser, String suppliedPassword) {
                return suppliedUser.equals(username) && password.equals(suppliedPassword);
            }
        });
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public void expectConcurrent(String... expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (String request : expectedRequests) {
            expectations.add(doGet(request));
        }
        addNonBlockingHandler(expectations);
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public void expectConcurrent(Collection<String> expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (String request : expectedRequests) {
            expectations.add(doGet(request));
        }
        addNonBlockingHandler(expectations);
    }

    /**
     * Expects the given requests to be made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public void expectConcurrent(ExpectedRequest... expectedCalls) {
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (ExpectedRequest call : expectedCalls) {
            expectations.add((ResourceExpectation) call);
        }
        addNonBlockingHandler(expectations);
    }

    private void addNonBlockingHandler(final Collection<? extends ResourceExpectation> expectations) {
        handler.addHandler(previous -> new ExpectAllRequestsThenReleaseAll(lock, serverId, timeout, previous, expectations, EXECUTOR_SERVICE));
    }

    /**
     * Returns an expectation that the given requests are made concurrently. Blocks each request until they have all been received then releases them all.
     */
    public ExpectedRequests concurrent(String... expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (String request : expectedRequests) {
            expectations.add(doGet(request));
        }
        return new DefaultExpectedRequests(previous -> new ExpectAllRequestsThenReleaseAll(lock, serverId, timeout, previous, expectations, EXECUTOR_SERVICE));
    }

    /**
     * Expects the given groups of requests. Asserts that at most one group runs at a given time, but that the groups can happen in any order.
     */
    public void expectInAnyOrder(ExpectedRequests... expectedRequests) {
        List<DefaultExpectedRequests> expectations = new ArrayList<>();
        for (ExpectedRequests expectedRequest : expectedRequests) {
            expectations.add((DefaultExpectedRequests) expectedRequest);
        }
        handler.addHandler(previous -> new ExpectInAnyOrder(lock, previous, expectations));
    }

    /**
     * Expects the given requests to be made. Blocks until the given number of concurrent requests have been received, then releases one of the requests and blocks again.
     * Repeats until all of the requests have been received.
     */
    public void expectConcurrent(int concurrent, String... expectedRequests) {
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (String request : expectedRequests) {
            expectations.add(doGet(request));
        }
        handler.addHandler(previous -> new ExpectMaxNRequestsThenReleaseOne(lock, serverId, timeout, concurrent, previous, expectations, EXECUTOR_SERVICE));
    }

    /**
     * Expect a HEAD request to the given path.
     */
    public ExpectedRequest head(String path) {
        return new ExpectMethodAndRunAction("HEAD", normalizePath(path), new ErroringAction<HttpExchange>() {
            @Override
            protected void doExecute(HttpExchange exchange) throws Exception {
                exchange.sendResponseHeaders(200, -1);
            }
        });
    }

    /**
     * Expect a GET request to the given path and run the given action to create the response.
     */
    public ExpectedRequest get(String path, Action<? super HttpExchange> action) {
        return new ExpectMethodAndRunAction("GET", normalizePath(path), action);
    }

    /**
     * Expect a GET request to the given path. By default, sends a 200 response with some arbitrary content to the client.
     *
     * <p>The returned {@link BuildableExpectedRequest} can be used to modify the behaviour or expectations.
     */
    public BuildableExpectedRequest get(String path) {
        return doGet(path);
    }

    private ExpectMethod doGet(String path) {
        return new ExpectMethod("GET", normalizePath(path), timeout, lock);
    }

    /**
     * Expect a PUT request to the given path, discard the request body
     */
    public ExpectedRequest put(String path) {
        return new ExpectMethodAndRunAction("PUT", normalizePath(path), new SendEmptyResponse());
    }

    /**
     * Expect a POST request to the given path and run the given action to create the response.
     */
    public ExpectedRequest post(String path, Action<? super HttpExchange> action) {
        return new ExpectMethodAndRunAction("POST", normalizePath(path), action);
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
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (String call : expectedCalls) {
            expectations.add(doGet(call));
        }
        return addBlockingHandler(concurrent, expectations);
    }

    /**
     * Expects exactly the given number of calls to be made concurrently from any combination of the optionally expected calls. Blocks each call until they are explicitly released.
     * Since the expectations are optional, they are still considered "complete" even if not all expected calls have been received.
     */
    public BlockingHandler expectOptionalAndBlock(int concurrent, String... optionalExpectedCalls) {
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (String call : optionalExpectedCalls) {
            expectations.add(doGet(call));
        }
        return addBlockingOptionalHandler(concurrent, expectations);
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
        List<ResourceExpectation> expectations = new ArrayList<>();
        for (ExpectedRequest request : expectedRequests) {
            expectations.add((ResourceExpectation) request);
        }
        return addBlockingHandler(concurrent, expectations);
    }

    private BlockingHandler addBlockingHandler(final int concurrent, final Collection<? extends ResourceExpectation> expectations) {
        return handler.addHandler(previous -> new ExpectMaxNConcurrentRequests(lock, serverId, timeout, concurrent, previous, expectations));
    }

    private BlockingHandler addBlockingOptionalHandler(final int concurrent, final Collection<? extends ResourceExpectation> expectations) {
        return handler.addHandler(previous -> new MaybeNConcurrentRequests(lock, serverId, timeout, concurrent, previous, expectations));
    }

    /**
     * Expects the given request to be made. Releases the request as soon as it is received.
     */
    public void expect(String expectedCall) {
        addNonBlockingHandler(Collections.singleton(doGet(expectedCall)));
    }

    /**
     * Expects the given request to be made. Blocks until the request is explicitly released using one of the methods on {@link BlockingHandler}.
     */
    public BlockingHandler expectAndBlock(String expectedCall) {
        return addBlockingHandler(1, Collections.singleton(doGet(expectedCall)));
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
        handler.waitForCompletion();
        running = false;
        // Stop is very slow, clean it up later
        EXECUTOR_SERVICE.execute(new Runnable() {
            @Override
            public void run() {
                server.stop(10);
            }
        });
    }

    @Override
    public void resetExpectations() {
        handler.resetExpectations();
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
     * To help with debugging the underlying {@link com.sun.net.httpserver.HttpServer}.
     */
    public static void enableServerLogging() {
        final ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        final Logger logger = Logger.getLogger("com.sun.net.httpserver");
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    static String normalizePath(String path) {
        if (path.startsWith("/")) {
            return path.substring(1);
        }
        return path;
    }

    public interface ExpectedRequests {
    }

    /**
     * Represents an expectation about a particular HTTP request.
     */
    public interface ExpectedRequest {
    }

    /**
     * A mutable expectation about a particular HTTP request.
     */
    public interface BuildableExpectedRequest extends ExpectedRequest {
        /**
         * Verifies that the user agent provided in the request matches the given criteria.
         *
         * @return this
         */
        BuildableExpectedRequest expectUserAgent(Matcher expectedUserAgent);

        /**
         * Sends a 404 response with some arbitrary content as the response body.
         *
         * @return this
         */
        BuildableExpectedRequest missing();

        /**
         * Sends a 500 response with some arbitrary content as the response body.
         *
         * @return this
         */
        BuildableExpectedRequest broken();

        /**
         * Sends a 200 response with the contents of the given file as the response body.
         *
         * @return this
         */
        BuildableExpectedRequest sendFile(File file);

        /**
         * Sends a 200 response with the given text (UTF-8 encoded) as the response body.
         *
         * @return this
         */
        BuildableExpectedRequest send(String content);

        /**
         * Sends a 200 response with the given content. Returns 1K of the content then blocks waiting for {@link BlockingRequest#release()} before returning the remainder to the client.
         */
        BlockingRequest sendSomeAndBlock(byte[] content);
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

        /**
         * Waits for the expected number of concurrent requests to be received or until the given {@link FailureTracker} provides a
         * failure captured during execution which should be reported without waiting further.
         */
        void waitForAllPendingCalls(FailureTracker failureTracker);
    }

    public interface FailureTracker {
        FailureTracker NO_FAILURE_TRACKER = new FailureTracker() {
            @Override
            public RuntimeException getFailure() {
                return null;
            }
        };

        RuntimeException getFailure();
    }

    private static class SendEmptyResponse extends ErroringAction<HttpExchange> {
        @Override
        protected void doExecute(HttpExchange httpExchange) throws Exception {
            httpExchange.sendResponseHeaders(200, 0);
        }
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

    protected enum Scheme {
        HTTP("http", "127.0.0.1"),
        HTTPS("https", "localhost");

        private final String scheme;
        private final String host;

        Scheme(String scheme, String host) {
            this.scheme = scheme;
            this.host = host;
        }
    }
}
