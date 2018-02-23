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
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

class CyclicBarrierRequestHandler implements TrackingHttpHandler, WaitPrecondition {
    private final Timer timer = Time.startTimer();
    private final Lock lock;
    private final Condition condition;
    private final List<String> received = new ArrayList<String>();
    private final Map<String, ResourceHandler> pending;
    private final int timeoutMs;
    private final WaitPrecondition previous;
    private long mostRecentEvent;
    private AssertionError failure;

    CyclicBarrierRequestHandler(Lock lock, int timeoutMs, WaitPrecondition previous, Collection<? extends ResourceExpectation> expectations) {
        this.lock = lock;
        condition = lock.newCondition();
        this.timeoutMs = timeoutMs;
        this.previous = previous;
        pending = new TreeMap<String, ResourceHandler>();
        for (ResourceExpectation expectation : expectations) {
            // Can wait on request if previous handler allows waiting
            ResourceHandler handler = expectation.create(previous);
            pending.put(handler.getPath(), handler);
        }
    }

    @Override
    public WaitPrecondition getWaitPrecondition() {
        return this;
    }

    @Override
    public void assertCanWait() throws AssertionError {
        lock.lock();
        try {
            // Can wait if this handler has completed or if the previous handler allows waiting
            if (pending.isEmpty()) {
                // Already completed
                return;
            }
            previous.assertCanWait();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ResourceHandler handle(int id, HttpExchange httpExchange) throws Exception {
        ResourceHandler handler;
        lock.lock();
        try {
            if (pending.isEmpty()) {
                // barrier open, let it travel on
                return null;
            }
            if (failure != null) {
                // Busted
                throw failure;
            }

            long now = timer.getElapsedMillis();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            String path = httpExchange.getRequestURI().getPath().substring(1);
            handler = pending.get(path);
            if (handler == null || !handler.getMethod().equals(httpExchange.getRequestMethod())) {
                failure = new AssertionError(String.format("Unexpected request %s %s received. Waiting for %s, already received %s.", httpExchange.getRequestMethod(), path, pending.keySet(), received));
                condition.signalAll();
                throw failure;
            }

            pending.remove(path);
            received.add(path);
            if (pending.isEmpty()) {
                condition.signalAll();
            }

            while (!pending.isEmpty() && failure == null) {
                long waitMs = mostRecentEvent + timeoutMs - timer.getElapsedMillis();
                if (waitMs < 0) {
                    System.out.println(String.format("[%d] timeout waiting for other requests", id));
                    failure = new AssertionError(String.format("Timeout waiting for expected requests to be received. Still waiting for %s, received %s.", pending.keySet(), received));
                    condition.signalAll();
                    throw failure;
                }
                System.out.println(String.format("[%d] waiting for other requests. Still waiting for %s", id, pending.keySet()));
                condition.await(waitMs, TimeUnit.MILLISECONDS);
            }

            if (failure != null) {
                // Failed in another thread
                System.out.println(String.format("[%d] failure in another thread", id));
                throw failure;
            }
        } finally {
            lock.unlock();
        }

        // All requests completed, write response
        return handler;
    }

    public void assertComplete() {
        lock.lock();
        try {
            if (failure != null) {
                throw failure;
            }
            if (!pending.isEmpty()) {
                throw new AssertionError(String.format("Did not receive expected requests. Waiting for %s, received %s", pending.keySet(), received));
            }
        } finally {
            lock.unlock();
        }
    }
}
