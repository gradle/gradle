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

package org.gradle.test.fixtures.server.http

import com.google.common.base.Preconditions
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.junit.rules.ExternalResource

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class HttpBuildCacheServer extends ExternalResource implements HttpServerFixture {
    private final TestDirectoryProvider provider
    private TestFile cacheDir
    int blockIncomingConnectionsForSeconds = 0
    private final List<Responder> responders = []

    HttpBuildCacheServer(TestDirectoryProvider provider) {
        this.provider = provider
    }

    TestFile getCacheDir() {
        Preconditions.checkNotNull(cacheDir)
    }

    List<TestFile> listCacheFiles() {
        cacheDir.listFiles().findAll { it.name ==~ /\p{XDigit}{${Hashing.defaultFunction().hexDigits}}/ }.sort()
    }

    void deleteCacheFiles() {
        listCacheFiles().each { it.delete() }
    }

    @Override
    HttpResourceHandler getCustomHandler() {
        return new HttpResourceHandler() {
            @Override
            void handle(String target, HttpRequest request, HttpResponse response) throws IOException {
                if (blockIncomingConnectionsForSeconds > 0) {
                    try {
                        new CountDownLatch(1).await(blockIncomingConnectionsForSeconds, TimeUnit.SECONDS)
                    } catch (InterruptedException ignore) {
                    }
                }
                for (responder in responders) {
                    if (!responder.respond(request, response)) {
                        return
                    }
                }
                serve(request, response)
            }
        }
    }

    private void serve(HttpRequest request, HttpResponse response) {
        File file = new File(cacheDir, request.pathInfo)
        switch (request.method) {
            case 'GET':
                if (file.isFile()) {
                    byte[] bytes = file.bytes
                    response.setContentLength(bytes.length)
                    response.setContentType("application/octet-stream")
                    response.outputStream << bytes
                } else {
                    response.sendError(404)
                }
                break
            case 'PUT':
                if (file.exists()) {
                    file.delete()
                }
                file.parentFile.mkdirs()
                file.withOutputStream { out ->
                    out << request.inputStream
                }
                response.setStatus(204)
                break
            case 'DELETE':
                if (!file.exists()) {
                    response.sendError(404)
                } else if (file.delete()) {
                    response.setStatus(204)
                } else {
                    response.sendError(500)
                }
                break
            default:
                response.sendError(405)
        }
    }

    interface Responder {
        /**
         * Return false to prevent further processing.
         */
        boolean respond(HttpRequest request, HttpResponse response) throws IOException
    }

    HttpBuildCacheServer addResponder(Responder responder) {
        responders << responder
        this
    }

    @Override
    void start() {
        cacheDir = provider.testDirectory.createDir('http-cache-dir')
        HttpServerFixture.super.start()
    }

    @Override
    protected void after() {
        stop()
    }

    void withBasicAuth(String username, String password) {
        requireAuthentication(username, password)
    }
}
