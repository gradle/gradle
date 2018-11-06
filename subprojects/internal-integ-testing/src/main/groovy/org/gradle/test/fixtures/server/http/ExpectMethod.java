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
import org.hamcrest.Matcher;

import java.io.File;
import java.io.IOException;

import static org.gradle.test.fixtures.server.http.ExpectMethodAndRunAction.removeLeadingSlash;

class ExpectMethod implements ResourceHandler, BlockingHttpServer.BuildableExpectedRequest, ResourceExpectation {
    private final String method;
    private final String path;
    private ResponseProducer producer = new ResponseProducer() {
        @Override
        public void writeTo(int requestId, HttpExchange exchange) throws IOException {
            responseBody.writeTo(requestId, exchange);
        }
    };
    private ResponseProducer responseBody = new SendFixedContent(200, "hi");

    ExpectMethod(String method, String path) {
        this.method = method;
        this.path = removeLeadingSlash(path);
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
        return this;
    }

    @Override
    public BlockingHttpServer.BuildableExpectedRequest expectUserAgent(Matcher expectedUserAgent) {
        producer = new UserAgentVerifier(expectedUserAgent, producer);
        return this;
    }

    @Override
    public BlockingHttpServer.BuildableExpectedRequest missing() {
        this.responseBody = new SendFixedContent(404, "not found");
        return this;
    }

    @Override
    public BlockingHttpServer.BuildableExpectedRequest broken() {
        this.responseBody = new SendFixedContent(500, "broken");
        return this;
    }

    @Override
    public BlockingHttpServer.BuildableExpectedRequest send(String content) {
        this.responseBody = new SendFixedContent(200, content);
        return this;
    }

    @Override
    public BlockingHttpServer.BuildableExpectedRequest sendFile(File file) {
        this.responseBody = new SendFileContents(file);
        return this;
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
            if (!expectedUserAgent.matches(exchange.getRequestHeaders().getFirst("User-Agent"))) {
                String message = "Unexpected user agent in request";
                byte[] bytes = message.getBytes(Charsets.UTF_8);
                exchange.sendResponseHeaders(500, bytes.length);
                exchange.getResponseBody().write(bytes);
                throw new AssertionError(message);
            }
            next.writeTo(requestId, exchange);
        }
    }
}
