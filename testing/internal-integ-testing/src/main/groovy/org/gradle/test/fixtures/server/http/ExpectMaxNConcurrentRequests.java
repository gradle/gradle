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

import com.sun.net.httpserver.HttpExchange;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.gradle.test.fixtures.server.http.BlockingHttpServer.getCurrentTimestamp;

class ExpectMaxNConcurrentRequests implements TrackingHttpHandler, WaitPrecondition, BlockingHttpServer.BlockingHandler {
    private final Lock lock;
    private final Condition condition;
    private final List<ResourceHandlerWrapper> received = new ArrayList<>();
    private final List<ResourceHandlerWrapper> released = new ArrayList<>();
    private final LinkedList<ResourceHandlerWrapper> notReleased = new LinkedList<>();
    private final List<ResourceHandlerWrapper> notReceived = new ArrayList<>();
    private final int testId;
    private final long timeoutMs;
    private final Clock clock = Time.clock();
    private int waitingFor;
    private final WaitPrecondition previous;
    private long mostRecentEvent;
    private boolean cancelled;
    private final ExpectationState state = new ExpectationState();

    ExpectMaxNConcurrentRequests(Lock lock, int testId, Duration timeout, int maxConcurrent, WaitPrecondition previous, Collection<? extends ResourceExpectation> expectedRequests) {
        if (expectedRequests.size() < maxConcurrent) {
            throw new IllegalArgumentException("Too few requests specified.");
        }
        this.lock = lock;
        this.condition = lock.newCondition();
        this.testId = testId;
        this.timeoutMs = timeout.toMillis();
        this.waitingFor = maxConcurrent;
        this.previous = previous;
        for (ResourceExpectation expectation : expectedRequests) {
            ResourceHandlerWrapper handler = new ResourceHandlerWrapper(lock, expectation, getWaitPrecondition(), isAutoRelease());
            notReceived.add(handler);
        }
    }

    protected boolean isAutoRelease() {
        return false;
    }

    @Override
    public WaitPrecondition getWaitPrecondition() {
        return this;
    }

    @Override
    public void assertCanWait() throws IllegalStateException {
        lock.lock();
        try {
            previous.assertCanWait();
            if (notReceived.isEmpty()) {
                // Have received all requests so downstream can wait.
                return;
            }
            if (!isAutoRelease()) {
                throw new IllegalStateException(String.format("Cannot wait as no requests have been released. Waiting for %s, received %s.", format(notReceived), format(received)));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean expecting(HttpExchange exchange) {
        if (notReceived.isEmpty()) {
            return false;
        }
        String path = exchange.getRequestURI().getPath().substring(1);
        ResourceHandlerWrapper handler = selectPending(notReceived, path);
        return handler != null;
    }

    @Override
    public ResponseProducer selectResponseProducer(int id, HttpExchange exchange) {
        ResourceHandlerWrapper handler;
        lock.lock();
        try {
            if (notReceived.isEmpty()) {
                // barrier open, let it travel on
                return null;
            }

            long now = clock.getCurrentTime();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            String path = exchange.getRequestURI().getPath().substring(1);
            handler = selectPending(notReceived, path);
            if (handler == null || !handler.getMethod().equals(exchange.getRequestMethod()) || waitingFor == 0) {
                ResponseProducer failure = state.unexpectedRequest(exchange.getRequestMethod(), path, describeCurrentState());
                condition.signalAll();
                return failure;
            }

            notReceived.remove(handler);
            notReleased.add(handler);
            received.add(handler);
            handler.received();
            waitingFor--;
            if (waitingFor == 0) {
                condition.signalAll();
            }

            if (state.isFailed()) {
                // Broken in another thread
                System.out.printf("[%s][%d] failure in another thread%n", getCurrentTimestamp(), id);
                return state.alreadyFailed(exchange.getRequestMethod(), path, describeCurrentState());
            }

            if (waitingFor == 0) {
                System.out.printf("[%s][%d] signalling all requests ready%n", getCurrentTimestamp(), id);
                onExpectedRequestsReceived(this, notReceived.size());
            }

            while (!handler.isReleased() && !state.isFailed() && !cancelled) {
                long waitMs = mostRecentEvent + timeoutMs - clock.getCurrentTime();
                if (waitMs < 0) {
                    ResponseProducer failure;
                    if (waitingFor > 0) {
                        System.out.printf("[%s][%d] timeout waiting for other requests%n", getCurrentTimestamp(), id);
                        failure = state.timeout(exchange.getRequestMethod(), path, "waiting for other requests", describeCurrentState());
                    } else {
                        System.out.printf("[%s][%d] timeout waiting to be released%n", getCurrentTimestamp(), id);
                        failure = state.timeout(exchange.getRequestMethod(), path, "waiting to be released", describeCurrentState());
                    }
                    condition.signalAll();
                    return failure;
                }
                System.out.printf("[%s][%d] waiting to be released. Still waiting for %s further requests, already received %s%n", getCurrentTimestamp(), id, waitingFor, format(received));
                try {
                    condition.await(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (state.isFailed()) {
                // Broken in another thread
                System.out.printf("[%s][%d] failure in another thread%n", getCurrentTimestamp(), id);
                if (waitingFor > 0) {
                    return state.failureWhileWaiting(exchange.getRequestMethod(), path, "waiting for other requests", describeCurrentState());
                } else {
                    return state.failureWhileWaiting(exchange.getRequestMethod(), path, "waiting to be released", describeCurrentState());
                }
            }
            if (cancelled) {
                return new ResponseProducer() {
                    @Override
                    public void writeTo(int requestId, HttpExchange exchange) {
                        try {
                            exchange.sendResponseHeaders(200, -1);
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                };
            }
        } finally {
            lock.unlock();
        }

        return handler;
    }

    /**
     * Called when the expected requests have been received and are now blocked waiting to be released.
     * Subclasses may choose to release some or all of the requests.
     */
    protected void onExpectedRequestsReceived(BlockingHttpServer.BlockingHandler handler, int yetToBeReceived) {
    }

    private String describeCurrentState() {
        return String.format("Waiting for %s further requests, received %s, released %s, not yet received %s", waitingFor, format(received), format(released), format(notReceived));
    }

    @Override
    public void cancelBlockedRequests() {
        lock.lock();
        try {
            cancelled = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void assertComplete(Collection<Throwable> failures) throws AssertionError {
        lock.lock();
        try {
            if (state.isFailed()) {
                // Already reported
                return;
            }
            if (!notReceived.isEmpty()) {
                failures.add(new AssertionError(String.format("Did not receive all expected requests. %s", describeCurrentState())));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(String path) {
        path = BlockingHttpServer.normalizePath(path);
        lock.lock();
        try {
            ResourceHandlerWrapper handler = selectPending(notReleased, path);
            if (handler == null) {
                throw new IllegalStateException("Expected request already released.");
            }
            if (!handler.isReceived()) {
                throw new IllegalStateException("Expected request not received, should wait for pending calls first.");
            }
            System.out.printf("[%s][%d] releasing %s%n", getCurrentTimestamp(), testId, handler);
            released.add(handler);
            handler.released();
            notReleased.remove(handler);
            doRelease(1);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void releaseAll() {
        lock.lock();
        try {
            if (!notReceived.isEmpty()) {
                throw new IllegalStateException("Expected requests not received, should wait for pending calls first.");
            }
            doReleaseAll();
        } finally {
            lock.unlock();
        }
    }

    protected void doReleaseAll() {
        lock.lock();
        try {
            int count = 0;
            for (ResourceHandlerWrapper resourceHandler : notReleased) {
                System.out.printf("[%s][%d] releasing %s%n", getCurrentTimestamp(), testId, resourceHandler.getDisplayName());
                released.add(resourceHandler);
                resourceHandler.released();
                count++;
            }
            notReleased.clear();
            doRelease(count);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(int count) {
        lock.lock();
        try {
            if (notReleased.size() < count) {
                throw new IllegalStateException("Too few requests released, should wait for pending calls first.");
            }
            for (int i = 0; i < count; i++) {
                ResourceHandlerWrapper resourceHandler = notReleased.removeFirst();
                System.out.printf("[%s][%d] releasing %s%n", getCurrentTimestamp(), testId, resourceHandler.getDisplayName());
                released.add(resourceHandler);
                resourceHandler.released();
            }
            doRelease(count);
        } finally {
            lock.unlock();
        }
    }

    private void doRelease(int count) {
        waitingFor = Math.min(notReceived.size(), waitingFor + count);
        System.out.printf("[%s][%d] now expecting %d further requests, received %s, released %s, not yet received %s%n", getCurrentTimestamp(), testId, waitingFor, format(received), format(released), format(notReceived));
        condition.signalAll();
    }


    @Override
    public void waitForAllPendingCalls() {
        waitForAllPendingCalls(BlockingHttpServer.FailureTracker.NO_FAILURE_TRACKER);
    }

    @Override
    public void waitForAllPendingCalls(BlockingHttpServer.FailureTracker failureTracker) {
        lock.lock();
        try {
            previous.assertCanWait();

            long now = clock.getCurrentTime();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            while (waitingFor > 0 && !state.isFailed() && failureTracker.getFailure() == null) {
                long waitMs = mostRecentEvent + timeoutMs - clock.getCurrentTime();
                if (waitMs < 0) {
                    System.out.printf("[%s][%d] timeout waiting for expected requests.%n", getCurrentTimestamp(), testId);
                    timeoutWaitingForRequests();
                    break;
                }
                System.out.printf("[%s][%d] waiting for %d further requests, received %s, released %s, not yet received %s%n", getCurrentTimestamp(), testId, waitingFor, format(received), format(released), format(notReceived));
                try {
                    condition.await(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (failureTracker.getFailure() != null) {
                throw failureTracker.getFailure();
            }
            if (state.isFailed()) {
                throw state.getWaitFailure(describeCurrentState());
            }
            System.out.printf("[%s][%d] expected requests received, received %s, released %s, not yet received %s%n", getCurrentTimestamp(), testId, format(received), format(released), format(notReceived));
        } finally {
            lock.unlock();
        }
    }

    private void timeoutWaitingForRequests() {
        state.timeout("waiting for expected requests", describeCurrentState());
        condition.signalAll();
    }

    private static String format(List<? extends ResourceHandlerWrapper> handlers) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (ResourceHandlerWrapper handler : handlers) {
            if (builder.length() > 1) {
                builder.append(", ");
            }
            builder.append(handler.getDisplayName());
        }
        builder.append("]");
        return builder.toString();
    }

    @Nullable
    private static <T extends ResourceHandler> T selectPending(List<T> handlers, String path) {
        for (T handler : handlers) {
            if (handler.getPath().equals(path)) {
                return handler;
            }
        }
        return null;
    }
}
