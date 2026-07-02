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
            if (file.isFile() && isUnderBaseDir(file)) {
                byte[] bytes = file.bytes
                exchange.sendResponseHeaders(200, bytes.length)
                OutputStream out = exchange.responseBody
                if (!"HEAD".equalsIgnoreCase(exchange.requestMethod)) {
                    out.write(bytes)
                }
                out.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
            }
        } finally {
            exchange.close()
        }
    }

    private boolean isUnderBaseDir(File file) {
        return file.canonicalFile.toPath().startsWith(baseDir.canonicalFile.toPath())
    }
}
