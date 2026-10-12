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

package org.gradle.performance

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.transform.CompileStatic
import org.gradle.test.fixtures.server.http.HttpServer

@CompileStatic
class StaticFileHandler implements HttpHandler {
    private final File baseDir

    StaticFileHandler(File baseDir) {
        this.baseDir = baseDir
    }

    @Override
    void handle(HttpExchange exchange) throws IOException {
        try {
            File file = new File(baseDir, exchange.requestURI.path)
            boolean head = "HEAD".equalsIgnoreCase(exchange.requestMethod)
            if (file.isFile() && isUnderBaseDir(file)) {
                sendBytes(exchange, "application/octet-stream", file.bytes, head)
            } else if (file.isDirectory() && isUnderBaseDir(file)) {
                sendBytes(exchange, "text/html;charset=utf-8", HttpServer.directoryListingHtml(file), head)
            } else {
                exchange.sendResponseHeaders(404, -1)
            }
        } finally {
            exchange.close()
        }
    }

    private static void sendBytes(HttpExchange exchange, String contentType, byte[] bytes, boolean head) {
        exchange.responseHeaders.set("Content-Type", contentType)
        exchange.sendResponseHeaders(200, head ? -1 : bytes.length)
        if (!head) {
            OutputStream out = exchange.responseBody
            out.write(bytes)
            out.close()
        }
    }

    private boolean isUnderBaseDir(File file) {
        return file.canonicalFile.toPath().startsWith(baseDir.canonicalFile.toPath())
    }
}
