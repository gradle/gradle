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
import org.gradle.internal.time.TrueTimeProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

class CyclicBarrierAnyOfRequestHandler extends TrackingHttpHandler implements BlockingHttpServer.BlockingHandler {
    private final Lock lock;
    private final Condition condition;
    private final List<String> received = new ArrayList<String>();
    private final List<String> released = new ArrayList<String>();
    private final Map<String, ResourceHandler> expected = new TreeMap<String, ResourceHandler>();
    private final int testId;
    private final int timeoutMs;
    private final TrueTimeProvider timeProvider = new TrueTimeProvider();
    private int waitingFor;
    private long mostRecentEvent;
    private AssertionError failure;

    CyclicBarrierAnyOfRequestHandler(Lock lock, int testId, int timeoutMs, int maxConcurrent, Collection<? extends ResourceHandler> expectedCalls) {
        this.lock = lock;
        this.condition = lock.newCondition();
        this.testId = testId;
        this.timeoutMs = timeoutMs;
        this.waitingFor = maxConcurrent;
        for (ResourceHandler call : expectedCalls) {
            expected.put(call.getPath(), call);
        }
    }

    @Override
    public ResourceHandler handle(int id, HttpExchange httpExchange) throws Exception {
        ResourceHandler handler;
        lock.lock();
        try {
            if (expected.isEmpty()) {
                // barrier open, let it travel on
                return null;
            }
            if (failure != null) {
                // Busted
                throw failure;
            }

            long now = timeProvider.getCurrentTimeForDuration();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            String path = httpExchange.getRequestURI().getPath().substring(1);
            if (!expected.containsKey(path) || waitingFor == 0) {
                failure = new AssertionError(String.format("Unexpected request to '%s' received. Waiting for %s further requests, already received %s, released %s, still expecting %s.", path, waitingFor, received, released, expected.keySet()));
                condition.signalAll();
                throw failure;
            }

            handler = expected.remove(path);
            received.add(path);
            waitingFor--;
            if (waitingFor == 0) {
                condition.signalAll();
            }

            while (!released.contains(path) && failure == null) {
                long waitMs = mostRecentEvent + timeoutMs - timeProvider.getCurrentTimeForDuration();
                if (waitMs < 0) {
                    if (waitingFor > 0) {
                        System.out.println(String.format("[%d] timeout waiting for other requests", id));
                        throw timeoutWaitingForRequests();
                    }
                    System.out.println(String.format("[%d] timeout waiting to be released", id));
                    failure = new AssertionError(String.format("Timeout waiting to be released. Waiting for %s further requests, received %s, released %s, not yet received %s.", waitingFor, received, released, expected.keySet()));
                    condition.signalAll();
                    throw failure;
                }
                System.out.println(String.format("[%d] waiting to be released. Still waiting for %s further requests, already received %s", id, waitingFor, received));
                condition.await(waitMs, TimeUnit.MILLISECONDS);
            }
            if (failure != null) {
                // Broken in another thread
                System.out.println(String.format("[%d] failure in another thread", id));
                throw failure;
            }
        } finally {
            lock.unlock();
        }

        return handler;
    }

    public void assertComplete() {
        lock.lock();
        try {
            if (failure != null) {
                throw failure;
            }
            if (!expected.isEmpty()) {
                throw new AssertionError(String.format("Did not handle all expected requests. Waiting for %d further requests, received %s, released %s, not yet received %s.", waitingFor, received, released, expected.keySet()));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(String path) {
        path = new SimpleResourceHandler(path).getPath();
        lock.lock();
        try {
            if (!received.contains(path)) {
                throw new IllegalStateException("Expected request not received, should wait for pending calls first.");
            }
            if (released.contains(path)) {
                throw new IllegalStateException("Expected request already released.");
            }
            System.out.println(String.format("[%d] releasing %s", testId, path));
            released.add(path);
            doRelease(1);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void releaseAll() {
        lock.lock();
        try {
            if (!expected.isEmpty()) {
                throw new IllegalStateException("Expected requests not received, should wait for pending calls first.");
            }
            int count = 0;
            for (String path : received) {
                if (!released.contains(path)) {
                    System.out.println(String.format("[%d] releasing %s", testId, path));
                    released.add(path);
                    count++;
                }
            }
            doRelease(count);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(int count) {
        lock.lock();
        try {
            int releaseCount = 0;
            for (int i = 0; releaseCount < count && i < received.size(); i++) {
                String call = received.get(i);
                if (!released.contains(call)) {
                    System.out.println(String.format("[%d] releasing %s", testId, call));
                    released.add(call);
                    releaseCount++;
                }
            }
            if (releaseCount != count) {
                throw new IllegalStateException("Too few requests released, should wait for pending calls first.");
            }
            doRelease(count);
        } finally {
            lock.unlock();
        }
    }

    private void doRelease(int count) {
        waitingFor = Math.min(expected.size(), waitingFor + count);
        System.out.println(String.format("[%d] now expecting %d further requests, received %s, released %s, not yet received %s", testId, waitingFor, received, released, expected.keySet()));
        condition.signalAll();
    }

    @Override
    public void waitForAllPendingCalls() {
        lock.lock();
        try {
            long now = timeProvider.getCurrentTimeForDuration();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            while (waitingFor > 0 && failure == null) {
                long waitMs = mostRecentEvent + timeoutMs - timeProvider.getCurrentTimeForDuration();
                if (waitMs < 0) {
                    System.out.println(String.format("[%d] timeout waiting for expected requests.", testId));
                    throw timeoutWaitingForRequests();
                }
                System.out.println(String.format("[%d] waiting for %d further requests, received %s, released %s, not yet received %s", testId, waitingFor, received, released, expected.keySet()));
                try {
                    condition.await(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
            System.out.println(String.format("[%d] expected requests received, received %s, released %s, not yet received %s", testId, received, released, expected.keySet()));
        }  finally {
            lock.unlock();
        }
    }

    private AssertionError timeoutWaitingForRequests() {
        failure = new AssertionError(String.format("Timeout waiting for expected requests. Waiting for %d further requests, received %s, released %s, not yet received %s.", waitingFor, received, released, expected.keySet()));
        condition.signalAll();
        throw failure;
    }
}
