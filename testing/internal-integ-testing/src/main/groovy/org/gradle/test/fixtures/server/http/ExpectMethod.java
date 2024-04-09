/*
 * Copyright 2018 the original author or authors.
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
import com.google.common.io.Files;
import com.sun.net.httpserver.HttpExchange;
import org.gradle.test.fixtures.server.http.BlockingHttpServer.BlockingRequest;
import org.gradle.test.fixtures.server.http.BlockingHttpServer.BuildableExpectedRequest;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.locks.Lock;

class ExpectMethod implements ResourceHandler, BuildableExpectedRequest, ResourceExpectation, BlockingRequest {
    private final String method;
    private final String path;
    private final Duration timeout;
    private final Lock lock;

    private ResponseProducer producer = new ResponseProducer() {
        @Override
        public void writeTo(int requestId, HttpExchange exchange) throws IOException {
            responseBody.writeTo(requestId, exchange);
        }
    };
    private ResponseProducer responseBody = new SendFixedContent(200, "hi");
    private BlockingRequest blockingRequest;
    private WaitPrecondition precondition;

    ExpectMethod(String method, String path, Duration timeout, Lock lock) {
        this.method = method;
        this.path = path;
        this.timeout = timeout;
        this.lock = lock;
    }

    @Override
    public String getMethod() {
        return method;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public ResourceHandler create(WaitPrecondition precondition) {
        this.precondition = precondition;
        return this;
    }

    @Override
    public void waitUntilBlocked() {
        if (blockingRequest == null) {
            throw new IllegalStateException("This request is not expected to block.");
        }
        blockingRequest.waitUntilBlocked();
    }

    @Override
    public void release() {
        if (blockingRequest == null) {
            throw new IllegalStateException("This request is not expected to block.");
        }
        blockingRequest.release();
    }

    @Override
    public BuildableExpectedRequest expectUserAgent(Matcher expectedUserAgent) {
        producer = new UserAgentVerifier(expectedUserAgent, producer);
        return this;
    }

    @Override
    public BuildableExpectedRequest missing() {
        replaceBody(new SendFixedContent(404, "not found"), null);
        return this;
    }

    @Override
    public BuildableExpectedRequest broken() {
        replaceBody(new SendFixedContent(500, "broken"), null);
        return this;
    }

    @Override
    public BuildableExpectedRequest send(String content) {
        replaceBody(new SendFixedContent(200, content), null);
        return this;
    }

    @Override
    public BlockingRequest sendSomeAndBlock(byte[] content) {
        if (content.length < 1024) {
            throw new IllegalArgumentException("Content is too short.");
        }
        SendPartialResponseThenBlock block = new SendPartialResponseThenBlock(lock, timeout, new WaitPrecondition() {
            @Override
            public void assertCanWait() throws IllegalStateException {
                ExpectMethod.this.precondition.assertCanWait();
            }
        }, content);
        replaceBody(block, block);
        return this;
    }

    @Override
    public BuildableExpectedRequest sendFile(File file) {
        replaceBody(new SendFileContents(file), null);
        return this;
    }

    private void replaceBody(ResponseProducer producer, BlockingRequest blocker) {
        this.responseBody = producer;
        this.blockingRequest = blocker;
    }

    @Override
    public void writeTo(int requestId, HttpExchange exchange) throws IOException {
        producer.writeTo(requestId, exchange);
    }

    private static class SendFixedContent implements ResponseProducer {
        private final String content;
        private final int statusCode;

        SendFixedContent(int statusCode, String content) {
            this.content = content;
            this.statusCode = statusCode;
        }

        @Override
        public void writeTo(int requestId, HttpExchange exchange) throws IOException {
            byte[] bytes = content.getBytes(Charsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static class SendFileContents implements ResponseProducer {
        private final File file;

        SendFileContents(File file) {
            this.file = file;
        }

        @Override
        public void writeTo(int requestId, HttpExchange exchange) throws IOException {
            exchange.sendResponseHeaders(200, file.length());
            Files.copy(file, exchange.getResponseBody());
        }
    }

    private static class UserAgentVerifier implements ResponseProducer {
        private final Matcher expectedUserAgent;
        private final ResponseProducer next;

        UserAgentVerifier(Matcher expectedUserAgent, ResponseProducer next) {
            this.expectedUserAgent = expectedUserAgent;
            this.next = next;
        }

        @Override
        public void writeTo(int requestId, HttpExchange exchange) throws IOException {
            String actual = exchange.getRequestHeaders().getFirst("User-Agent");
            if (!expectedUserAgent.matches(actual)) {
                StringDescription description = new StringDescription();
                description.appendText("Expected user agent ");
                expectedUserAgent.describeTo(description);
                description.appendText(" but ");
                expectedUserAgent.describeMismatch(actual, description);
                String message = description.toString();
                byte[] bytes = message.getBytes(Charsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                throw new AssertionError(message);
            }
            next.writeTo(requestId, exchange);
        }
    }
}
