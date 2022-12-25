/*
 * Copyright 2022 the original author or authors.
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;

class ExpectInAnyOrder implements TrackingHttpHandler {
    private final Lock lock;
    private final List<TrackingHttpHandler> expected;
    private final List<TrackingHttpHandler> available;
    private TrackingHttpHandler current = null;

    public ExpectInAnyOrder(Lock lock, WaitPrecondition previous, List<DefaultExpectedRequests> expected) {
        this.lock = lock;
        this.expected = new ArrayList<>();
        for (DefaultExpectedRequests expectedRequests : expected) {
            this.expected.add(expectedRequests.create(previous));
        }
        this.available = new ArrayList<>(this.expected);
    }

    @Override
    public boolean expecting(HttpExchange exchange) {
        lock.lock();
        try {
            if (current != null) {
                return current.expecting(exchange);
            }
            for (TrackingHttpHandler handler : available) {
                if (handler.expecting(exchange)) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    @Override
    public ResponseProducer selectResponseProducer(int id, HttpExchange exchange) {
        lock.lock();
        try {
            if (current != null) {
                ResponseProducer producer = current.selectResponseProducer(id, exchange);
                if (producer != null) {
                    return producer;
                }
                current = null;
            }
            for (TrackingHttpHandler handler : available) {
                if (handler.expecting(exchange)) {
                    current = handler;
                    available.remove(handler);
                    return current.selectResponseProducer(id, exchange);
                }
            }
            return null;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public WaitPrecondition getWaitPrecondition() {
        return () -> {
            for (TrackingHttpHandler handler : expected) {
                handler.getWaitPrecondition().assertCanWait();
            }
        };
    }

    @Override
    public void cancelBlockedRequests() {
        lock.lock();
        try {
            if (current != null) {
                current.cancelBlockedRequests();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void assertComplete(Collection<Throwable> failures) {
        for (TrackingHttpHandler handler : expected) {
            handler.assertComplete(failures);
        }
    }
}
