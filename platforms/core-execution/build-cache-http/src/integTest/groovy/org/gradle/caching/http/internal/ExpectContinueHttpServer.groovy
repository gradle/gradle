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

package org.gradle.caching.http.internal

import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A minimal raw-socket HTTP/1.1 server that gives full control over the {@code Expect: 100-continue}
 * handshake.
 *
 * <p>RFC 9110 §10.1.1 allows a server to answer an {@code Expect: 100-continue} request with a final
 * status code (e.g. an error or redirect) <em>instead of</em> {@code 100 Continue}, so that the client
 * withholds the request body. {@code com.sun.net.httpserver} (used by the general test fixtures) cannot
 * do this — it always auto-sends {@code 100 Continue} before the handler runs — so these specific tests
 * use this purpose-built server to exercise that behaviour deterministically.
 */
class ExpectContinueHttpServer {
    private ServerSocket serverSocket
    private ExecutorService executor
    private final List<Expectation> expectations = new CopyOnWriteArrayList<>()
    private volatile boolean running

    void start() {
        serverSocket = new ServerSocket()
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        executor = Executors.newCachedThreadPool()
        running = true
        executor.submit {
            while (running) {
                Socket socket
                try {
                    socket = serverSocket.accept()
                } catch (IOException ignore) {
                    return
                }
                executor.submit { handle(socket) }
            }
        }
    }

    void stop() {
        running = false
        serverSocket?.close()
        executor?.shutdownNow()
    }

    URI getUri() {
        return URI.create("http://127.0.0.1:${serverSocket.localPort}")
    }

    /** Respond with a final status code without sending 100 Continue, so the client withholds the body. */
    void expectReject(String method, String path, int status) {
        expectations << new Expectation(method: method, path: path, type: Type.REJECT, status: status)
    }

    /** Respond with a redirect without sending 100 Continue, so the client withholds the body and follows it. */
    void expectRedirect(String method, String path, int status, String location) {
        expectations << new Expectation(method: method, path: path, type: Type.REDIRECT, status: status, location: location)
    }

    /** Send 100 Continue, read the request body, and store it at the given destination. */
    void expectStore(String method, String path, File destination) {
        expectations << new Expectation(method: method, path: path, type: Type.STORE, destination: destination)
    }

    private void handle(Socket socket) {
        try {
            socket.withCloseable {
                InputStream input = new BufferedInputStream(socket.inputStream)
                OutputStream output = socket.outputStream

                String requestLine = readLine(input)
                if (!requestLine) {
                    return
                }
                String[] parts = requestLine.split(" ")
                String method = parts[0]
                String path = parts[1]

                int contentLength = 0
                boolean expectContinue = false
                String line
                while ((line = readLine(input)) != null && !line.isEmpty()) {
                    int colon = line.indexOf(":")
                    if (colon < 0) {
                        continue
                    }
                    String name = line.substring(0, colon).trim()
                    String value = line.substring(colon + 1).trim()
                    if (name.equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(value)
                    } else if (name.equalsIgnoreCase("Expect") && value.equalsIgnoreCase("100-continue")) {
                        expectContinue = true
                    }
                }

                Expectation expectation = expectations.find { it.method == method && it.path == path }
                if (expectation == null) {
                    writeResponse(output, 404, "Not Found", [:])
                    return
                }

                switch (expectation.type) {
                    case Type.STORE:
                        if (expectContinue) {
                            output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
                            output.flush()
                        }
                        byte[] body = input.readNBytes(contentLength)
                        expectation.destination.parentFile.mkdirs()
                        expectation.destination.bytes = body
                        writeResponse(output, 200, "OK", [:])
                        break
                    case Type.REDIRECT:
                        // No 100 Continue: the client must withhold the body and follow the redirect.
                        writeResponse(output, expectation.status, "Redirect", ["Location": expectation.location])
                        break
                    case Type.REJECT:
                        // No 100 Continue: the client must withhold the body.
                        writeResponse(output, expectation.status, "Rejected", [:])
                        break
                }
            }
        } catch (IOException ignore) {
            // Connection dropped; nothing to do in a test fixture.
        }
    }

    private static void writeResponse(OutputStream output, int status, String reason, Map<String, String> headers) {
        StringBuilder response = new StringBuilder("HTTP/1.1 ").append(status).append(" ").append(reason).append("\r\n")
        headers.each { name, value -> response.append(name).append(": ").append(value).append("\r\n") }
        response.append("Content-Length: 0\r\n")
        // Close after each response so redirect-following uses a fresh connection (no keep-alive bookkeeping).
        response.append("Connection: close\r\n\r\n")
        output.write(response.toString().getBytes(StandardCharsets.US_ASCII))
        output.flush()
    }


    /** Reads a single CRLF-terminated line, returning it without the trailing CRLF (null at end of stream). */
    private static String readLine(InputStream input) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream()
        int previous = -1
        int current
        while ((current = input.read()) != -1) {
            if (previous == (int) '\r' && current == (int) '\n') {
                byte[] bytes = buffer.toByteArray()
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII) // drop trailing '\r'
            }
            buffer.write(current)
            previous = current
        }
        return buffer.size() == 0 ? null : new String(buffer.toByteArray(), StandardCharsets.US_ASCII)
    }

    private enum Type {
        REJECT, REDIRECT, STORE
    }

    private static class Expectation {
        String method
        String path
        Type type
        int status
        String location
        File destination
    }
}
