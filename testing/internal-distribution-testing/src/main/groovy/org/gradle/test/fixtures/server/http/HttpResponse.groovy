/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.test.fixtures.server.http

import com.sun.net.httpserver.HttpExchange

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat

class HttpResponse {
    private final HttpExchange exchange
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream()
    private OutputStream outputStream
    private PrintWriter writer

    private int status = 200
    private Long explicitContentLength
    private boolean chunked
    private boolean committed

    HttpResponse(HttpExchange exchange) {
        this.exchange = exchange
    }

    void setStatus(int status) {
        this.status = status
    }

    void setStatus(int status, String reason) {
        this.status = status
    }

    void sendError(int status) {
        sendError(status, null)
    }

    void sendError(int status, String message) {
        this.status = status
        if (message != null && buffer.size() == 0) {
            buffer.write(message.getBytes(StandardCharsets.UTF_8))
        }
    }

    void setHeader(String name, String value) {
        if (name.equalsIgnoreCase("Transfer-Encoding")) {
            chunked = value != null && value.toLowerCase().contains("chunked")
            return
        }
        if (name.equalsIgnoreCase("Content-Length")) {
            explicitContentLength = value == null ? null : Long.parseLong(value)
            return
        }
        exchange.responseHeaders.set(name, value)
    }

    void addHeader(String name, String value) {
        if (name.equalsIgnoreCase("Transfer-Encoding") || name.equalsIgnoreCase("Content-Length")) {
            setHeader(name, value)
            return
        }
        exchange.responseHeaders.add(name, value)
    }

    void setDateHeader(String name, long date) {
        SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        format.setTimeZone(TimeZone.getTimeZone("GMT"))
        setHeader(name, format.format(new Date(date)))
    }

    void setContentType(String type) {
        setHeader("Content-Type", type)
    }

    String getContentType() {
        return exchange.responseHeaders.getFirst("Content-Type")
    }

    void setContentLength(int length) {
        explicitContentLength = (long) length
    }

    OutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new OutputStream() {
                @Override
                void write(int b) throws IOException { buffer.write(b) }

                @Override
                void write(byte[] b, int off, int len) throws IOException { buffer.write(b, off, len) }
            }
        }
        return outputStream
    }

    PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8))
        }
        return writer
    }

    boolean isCommitted() {
        return committed
    }

    void closeConnection() {
        committed = true
        exchange.close()
    }

    void commit(String requestMethod) throws IOException {
        if (committed) {
            return
        }
        committed = true
        if (writer != null) {
            writer.flush()
        }
        byte[] bytes = buffer.toByteArray()

        if ("HEAD".equalsIgnoreCase(requestMethod)) {
            long length = explicitContentLength != null ? explicitContentLength : bytes.length
            if (length >= 0) {
                exchange.responseHeaders.set("Content-Length", Long.toString(length))
            }
            exchange.sendResponseHeaders(status, -1)
            return
        }

        long sendLength
        if (chunked) {
            sendLength = 0 // com.sun.net.httpserver streams chunked when length is 0
        } else if (bytes.length == 0) {
            sendLength = -1 // no response body
        } else {
            sendLength = bytes.length
        }

        exchange.sendResponseHeaders(status, sendLength)
        OutputStream body = exchange.responseBody
        if (bytes.length > 0) {
            body.write(bytes)
        }
        body.close()
    }
}
