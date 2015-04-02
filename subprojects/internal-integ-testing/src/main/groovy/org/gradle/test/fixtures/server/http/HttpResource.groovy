/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.resource.RemoteResource

abstract class HttpResource implements RemoteResource {

    protected HttpServer server

    public HttpResource(HttpServer server) {
        this.server = server
    }

    @Override
    URI getUri() {
        return new URI(server.uri.scheme, server.uri.authority, path, null, null)
    }

    void allowGetOrHead() {
        server.allowGetOrHead(getPath(), file)
    }

    void expectGet() {
        server.expectGet(getPath(), file)
    }

    void allowGetOrHead(String userName, String password) {
        server.allowGetOrHead(getPath(), userName, password, file)
    }

    @Override
    void expectDownload() {
        expectGet()
    }

    @Override
    void expectDownloadBroken() {
        expectGetBroken()
    }

    @Override
    void expectDownloadMissing() {
        expectGetMissing()
    }

    @Override
    void expectMetadataRetrieve() {
        expectHead()
    }

    @Override
    void expectMetadataRetrieveBroken() {
        expectHeadBroken()
    }

    @Override
    void expectMetadataRetrieveMissing() {
        expectHeadMissing()
    }

    void expectGet(String userName, String password) {
        server.expectGet(getPath(), userName, password, file)
    }

    void expectGetBroken() {
        server.expectGetBroken(getPath())
    }

    void expectGetMissing(PasswordCredentials credentials = null) {
        server.expectGetMissing(getPath(), credentials)
    }

    void expectHead() {
        server.expectHead(getPath(), file)
    }

    void expectHeadMissing() {
        server.expectHeadMissing(path)
    }

    void expectHeadBroken() {
        server.expectHeadBroken(path)
    }

    void expectPut(PasswordCredentials credentials) {
        expectPut(200, credentials)
    }

    void expectPut(String username, String password) {
        server.expectPut(getPath(), username, password, getFile())
    }

    void expectPut(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(getPath(), getFile(), statusCode, credentials)
    }

    void expectPutBroken(PasswordCredentials credentials = null) {
        server.expectPut(getPath(), getFile(), 500, credentials)
    }

    abstract TestFile getFile();

    abstract protected String getPath();
}
