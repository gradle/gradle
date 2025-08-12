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

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import static org.gradle.test.fixtures.server.http.BlockingHttpServer.getCurrentTimestamp;

class SendPartialResponseThenBlock implements BlockingHttpServer.BlockingRequest, ResponseProducer {
    private final byte[] content;
    private final Lock lock;
    private final Duration timeout;
    private final Condition condition;
    private final WaitPrecondition precondition;
    private boolean requestStarted;
    private boolean released;
    private final Clock clock = Time.clock();
    private long mostRecentEvent;
    private AssertionError failure;

    SendPartialResponseThenBlock(Lock lock, Duration timeout, WaitPrecondition precondition, byte[] content) {
        this.lock = lock;
        this.timeout = timeout;
        condition = lock.newCondition();
        this.precondition = precondition;
        this.content = content;
    }

    @Override
    public void writeTo(int requestId, HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, content.length);
        exchange.getResponseBody().write(content, 0, 1024);
        exchange.getResponseBody().flush();
        lock.lock();
        try {
            long now = clock.getCurrentTime();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            requestStarted = true;
            condition.signalAll();
            while (!released && failure == null) {
                long waitMs = mostRecentEvent + timeout.toMillis() - clock.getCurrentTime();
                if (waitMs < 0) {
                    System.out.printf("[%s][%d] timeout waiting to be released after sending some content%n", getCurrentTimestamp(), requestId);
                    failure = new AssertionError("Timeout waiting to be released after sending some content.");
                    condition.signalAll();
                    throw failure;
                }
                try {
                    condition.await(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            lock.unlock();
        }
        exchange.getResponseBody().write(content, 1024, content.length - 1024);
    }

    @Override
    public void waitUntilBlocked() {
        lock.lock();
        try {
            precondition.assertCanWait();
            if (released) {
                throw new IllegalStateException("Response has already been released.");
            }

            long now = clock.getCurrentTime();
            if (mostRecentEvent < now) {
                mostRecentEvent = now;
            }

            while (!requestStarted && failure == null) {
                long waitMs = mostRecentEvent + timeout.toMillis() - clock.getCurrentTime();
                if (waitMs < 0) {
                    failure = new AssertionError("Timeout waiting request to block.");
                    condition.signalAll();
                    throw failure;
                }
                try {
                    condition.await(waitMs, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release() {
        lock.lock();
        try {
            if (!requestStarted) {
                throw new IllegalStateException("Response is not blocked, should call waitUntilBlocked() first.");
            }
            released = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
