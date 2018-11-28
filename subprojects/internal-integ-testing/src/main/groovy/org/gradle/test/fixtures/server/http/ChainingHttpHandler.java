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

import com.google.common.base.Charsets;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

class ChainingHttpHandler implements HttpHandler {
    private final int timeoutMs;
    private final AtomicInteger counter;
    private final List<TrackingHttpHandler> handlers = new CopyOnWriteArrayList<TrackingHttpHandler>();
    private final List<RequestOutcome> outcomes = new ArrayList<RequestOutcome>();
    private final Clock clock = Time.clock();
    private final Lock lock;
    private WaitPrecondition last;
    private boolean completed;
    private final Condition condition;
    private int requestsStarted;

    ChainingHttpHandler(Lock lock, int timeoutMs, AtomicInteger counter, WaitPrecondition first) {
        this.lock = lock;
        this.condition = lock.newCondition();
        this.timeoutMs = timeoutMs;
        this.counter = counter;
        this.last = first;
    }

    public <T extends TrackingHttpHandler> T addHandler(HandlerFactory<T> factory) {
        lock.lock();
        try {
            T handler = factory.create(last);
            handlers.add(handler);
            last = handler.getWaitPrecondition();
            return handler;
        } finally {
            lock.unlock();
        }
    }

    public void waitForCompletion() {
        for (TrackingHttpHandler handler : handlers) {
            handler.cancelBlockedRequests();
        }

        waitForRequestsToFinish();

        lock.lock();
        try {
            List<Throwable> failures = new ArrayList<Throwable>();
            for (RequestOutcome outcome : outcomes) {
                outcome.collectFailure(failures);
            }
            if (!completed) {
                for (TrackingHttpHandler handler : handlers) {
                    handler.assertComplete(failures);
                }
                handlers.clear();
                completed = true;
            }
            if (!failures.isEmpty()) {
                throw new DefaultMultiCauseException("Failed to handle all HTTP requests.", failures);
            }
        } finally {
            lock.unlock();
        }
    }

    private void waitForRequestsToFinish() {
        long completionTimeout = clock.getCurrentTime() + timeoutMs;
        for (RequestOutcome outcome : outcomes) {
            long waitTime = completionTimeout - clock.getCurrentTime();
            outcome.awaitCompletion(waitTime);
        }
    }

    @Override
    public void handle(HttpExchange httpExchange) {
        try {
            int id = counter.incrementAndGet();

            RequestOutcome outcome = requestStarted(httpExchange);
            System.out.println(String.format("[%d] handling %s", id, outcome.getDisplayName()));

            try {
                ResponseProducer responseProducer = selectProducer(id, httpExchange, outcome);
                if (responseProducer != null) {
                    System.out.println(String.format("[%d] sending response for %s", id, outcome.getDisplayName()));
                    responseProducer.writeTo(id, httpExchange);
                } else {
                    System.out.println(String.format("[%d] sending error response for unexpected request", id));
                    if (outcome.method.equals("HEAD")) {
                        httpExchange.sendResponseHeaders(500, -1);
                    } else {
                        byte[] message = String.format("Failed request %s", outcome.getDisplayName()).getBytes(Charsets.UTF_8);
                        httpExchange.sendResponseHeaders(500, message.length);
                        httpExchange.getResponseBody().write(message);
                    }
                }
            } catch (Throwable t) {
                System.out.println(String.format("[%d] handling %s failed with exception", id, outcome.getDisplayName()));
                requestFailed(outcome, t);
            } finally {
                requestCompleted(outcome);
            }
        } finally {
            httpExchange.close();
        }
    }

    private RequestOutcome requestStarted(HttpExchange httpExchange) {
        lock.lock();
        RequestOutcome outcome;
        try {
            outcome = new RequestOutcome(lock, httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath());
            outcomes.add(outcome);
        } finally {
            lock.unlock();
        }
        return outcome;
    }

    private void requestFailed(RequestOutcome outcome, Throwable t) {
        lock.lock();
        try {
            if (outcome.failure == null) {
                outcome.failure = new AssertionError(String.format("Failed to handle %s", outcome.getDisplayName()), t);
            }
        } finally {
            lock.unlock();
        }
    }

    private void requestCompleted(RequestOutcome outcome) {
        outcome.completed();
    }

    /**
     * Returns null on failure.
     */
    @Nullable
    private ResponseProducer selectProducer(int id, HttpExchange httpExchange, RequestOutcome outcome) {
        lock.lock();
        try {
            requestsStarted++;
            condition.signalAll();
            if (completed) {
                System.out.println(String.format("[%d] received request %s %s after HTTP server has stopped.", id, httpExchange.getRequestMethod(), httpExchange.getRequestURI()));
                return null;
            }
            for (TrackingHttpHandler handler : handlers) {
                ResponseProducer responseProducer = handler.selectResponseProducer(id, httpExchange);
                if (responseProducer != null) {
                    return responseProducer;
                }
            }
            System.out.println(String.format("[%d] unexpected request %s %s", id, httpExchange.getRequestMethod(), httpExchange.getRequestURI()));
            outcome.failure = new AssertionError(String.format("Received unexpected request %s %s", httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath()));
        } catch (Throwable t) {
            System.out.println(String.format("[%d] error during handling of request %s %s", id, httpExchange.getRequestMethod(), httpExchange.getRequestURI()));
            outcome.failure = new AssertionError(String.format("Failed to handle %s %s", httpExchange.getRequestMethod(), httpExchange.getRequestURI().getPath()), t);
        } finally {
            lock.unlock();
        }
        return null;
    }

    void waitForRequests(int requestCount) {
        lock.lock();
        try {
            while (this.requestsStarted < requestCount) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private static class RequestOutcome {
        final Lock lock;
        final Condition condition;
        final String method;
        final String url;
        Throwable failure;
        boolean completed;

        public RequestOutcome(Lock lock, String method, String url) {
            this.lock = lock;
            this.condition = lock.newCondition();
            this.method = method;
            this.url = url;
        }

        String getDisplayName() {
            return method + " " + url;
        }

        public void completed() {
            lock.lock();
            try {
                completed = true;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public void awaitCompletion(long waitTime) {
            lock.lock();
            try {
                while (!completed) {
                    condition.await(waitTime, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            } finally {
                lock.unlock();
            }
        }

        public void collectFailure(Collection<Throwable> failures) {
            if (failure != null) {
                failures.add(failure);
            }
            if (!completed) {
                failures.add(new AssertionError(String.format("Request %s has not yet completed.", getDisplayName())));
            }
        }
    }

    interface HandlerFactory<T extends TrackingHttpHandler> {
        T create(WaitPrecondition previous);
    }
}
