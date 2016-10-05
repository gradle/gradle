/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.test.fixtures.file.TestFile

class HttpDirectoryResource extends AbstractHttpResource {
    final String path
    private final TestFile directory

    HttpDirectoryResource(HttpServer server, String path, TestFile directory) {
        super(server)
        this.directory = directory
        this.path = path
        this.server = server
    }

    @Override
    public void expectGet() {
        server.expectGetDirectoryListing(path, directory)
    }

    public void allowGet() {
        server.allowGetDirectoryListing(path, directory)
    }

    @Override
    public void expectGetMissing() {
        server.expectGetMissing(path)
    }

    @Override
    public void expectGetBroken() {
        server.expectGetBroken(path)
    }

    @Override
    void expectGetRevalidate() {
        server.expectGetRevalidate(path, directory)
    }

    @Override
    void expectHead() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectHeadBroken() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectHeadMissing() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectHeadRevalidate() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectPut() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectPutBroken() {
        throw new UnsupportedOperationException()
    }
}
