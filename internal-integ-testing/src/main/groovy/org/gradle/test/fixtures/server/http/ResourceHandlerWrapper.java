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

import java.io.IOException;
import java.util.concurrent.locks.Lock;

class ResourceHandlerWrapper implements ResourceHandler, WaitPrecondition {
    private final Lock lock;
    private final ResourceHandler handler;
    private final WaitPrecondition owner;
    private final boolean autoRelease;
    private boolean started;
    private boolean received;

    ResourceHandlerWrapper(Lock lock, ResourceExpectation expectation, WaitPrecondition owner, boolean isAutoRelease) {
        this.lock = lock;
        handler = expectation.create(this);
        this.owner = owner;
        this.autoRelease = isAutoRelease;
    }

    public String getDisplayName() {
        return handler.getMethod() + " /" + handler.getPath();
    }

    void received() {
        lock.lock();
        try {
            received = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isReceived() {
        lock.lock();
        try {
            return received;
        } finally {
            lock.unlock();
        }
    }

    void released() {
        lock.lock();
        try {
            started = true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isReleased() {
        lock.lock();
        try {
            return started;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public String getPath() {
        return handler.getPath();
    }

    @Override
    public String getMethod() {
        return handler.getMethod();
    }

    @Override
    public void assertCanWait() throws IllegalStateException {
        lock.lock();
        try {
            owner.assertCanWait();
            if (!autoRelease && !started) {
                throw new IllegalStateException(String.format("Cannot wait as request %s has not been released yet.", getDisplayName()));
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeTo(int requestId, HttpExchange exchange) throws IOException {
        handler.writeTo(requestId, exchange);
    }
}
