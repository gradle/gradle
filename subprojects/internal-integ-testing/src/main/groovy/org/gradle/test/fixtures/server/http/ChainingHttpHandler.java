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
import com.sun.net.httpserver.HttpHandler;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class ChainingHttpHandler implements HttpHandler {
    private final AtomicInteger counter = new AtomicInteger();
    private final List<TrackingHttpHandler> handlers = new CopyOnWriteArrayList<TrackingHttpHandler>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<Throwable>();
    private boolean completed;

    public void addHandler(TrackingHttpHandler handler) {
        handlers.add(handler);
    }

    public void assertComplete() {
        if (!completed) {
            for (TrackingHttpHandler handler : handlers) {
                try {
                    handler.assertComplete();
                } catch (Throwable t) {
                    failures.add(t);
                }
            }
            completed = true;
        }
        if (!failures.isEmpty()) {
            throw new DefaultMultiCauseException("Failed to handle all HTTP requests.", failures);
        }
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        int id = counter.incrementAndGet();
        System.out.println(String.format("[%d] handling %s %s", id, httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath()));

        try {
            for (TrackingHttpHandler handler : handlers) {
                if (handler.handle(id, httpExchange)) {
                    httpExchange.close();
                    System.out.println(String.format("[%d] handled", id));
                    return;
                }
            }
            failures.add(new AssertionError(String.format("Received unexpected request %s %s", httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath())));
        } catch (Throwable t) {
            failures.add(new AssertionError(String.format("Failed to handle %s %s", httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath()), t));
        }

        System.out.println(String.format("[%d] sending error response", id));
        byte[] message = String.format("Failed %s request to %s", httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath()).getBytes();
        httpExchange.sendResponseHeaders(500, message.length);
        httpExchange.getResponseBody().write(message);
        httpExchange.close();
    }
}
