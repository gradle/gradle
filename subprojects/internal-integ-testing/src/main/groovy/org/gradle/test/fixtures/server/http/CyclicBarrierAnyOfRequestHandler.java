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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class CyclicBarrierAnyOfRequestHandler extends TrackingHttpHandler implements BlockingHttpServer.BlockingHandler {
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final List<String> received = new ArrayList<String>();
    private final Set<String> released = new HashSet<String>();
    private final Map<String, ResourceHandler> expected = new HashMap<String, ResourceHandler>();
    private final int timeoutMs;
    private int waitingFor;
    private AssertionError failure;

    CyclicBarrierAnyOfRequestHandler(int timeoutMs, int maxConcurrent, Collection<? extends ResourceHandler> expectedCalls) {
        this.timeoutMs = timeoutMs;
        this.waitingFor = maxConcurrent;
        for (ResourceHandler call : expectedCalls) {
            expected.put(call.getPath(), call);
        }
    }

    @Override
    public boolean handle(int id, HttpExchange httpExchange) throws Exception {
        Date expiry = new Date(new TrueTimeProvider().getCurrentTime() + timeoutMs);
        ResourceHandler handler;
        lock.lock();
        try {
            if (expected.isEmpty()) {
                // barrier open, let it travel on
                return false;
            }
            if (failure != null) {
                // Busted
                throw failure;
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
                System.out.println(String.format("[%d] waiting to be released", id));
                if (!condition.awaitUntil(expiry)) {
                    failure = new AssertionError(String.format("Timeout waiting to be released. Waiting for %s further requests, received %s, released %s, still expecting %s.", waitingFor, received, released, expected.keySet()));
                    condition.signalAll();
                    throw failure;
                }
            }
            if (failure != null) {
                // Broken in another thread
                throw failure;
            }
        } finally {
            lock.unlock();
        }

        handler.writeTo(httpExchange);
        return true;
    }

    public void assertComplete() {
        lock.lock();
        try {
            if (!expected.isEmpty()) {
                throw new AssertionError(String.format("Did not handle all expected requests. Waiting for %d further requests, received %s, released %s, still expecting %s.", waitingFor, received, released, expected.keySet()));
            }
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
                    System.out.println(String.format("[release] releasing %s", call));
                    released.add(call);
                    releaseCount++;
                }
            }
            if (releaseCount != count) {
                throw new IllegalStateException("Too few requests released, should wait for pending calls first.");
            }
            waitingFor = Math.min(expected.size(), waitingFor + count);
            System.out.println(String.format("[release] now expecting %d further requests", waitingFor));
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void waitForAllPendingCalls() {
        Date expiry = new Date(new TrueTimeProvider().getCurrentTime() + timeoutMs);
        lock.lock();
        try {
            while (waitingFor > 0 && failure == null) {
                System.out.println(String.format("[wait] waiting for %d further requests, received %s, released %s, still expecting %s", waitingFor, received, released, expected.keySet()));
                try {
                    if (!condition.awaitUntil(expiry)) {
                        throw new AssertionError(String.format("Timeout waiting for expected requests. Waiting for %d further requests, received %s, released %s, still expecting %s.", waitingFor, received, released, expected.keySet()));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (failure != null) {
                throw new AssertionError("Could not wait for pending requests due to a request failure", failure);
            }
            System.out.println(String.format("[wait] expected requests received, received %s, released %s, still expecting %s", received, released, expected.keySet()));
        }  finally {
            lock.unlock();
        }
    }
}
