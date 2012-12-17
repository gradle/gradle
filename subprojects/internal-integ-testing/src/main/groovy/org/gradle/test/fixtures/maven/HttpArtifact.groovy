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

package org.gradle.test.fixtures.maven

import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TestFile

abstract class HttpArtifact extends HttpResource {

    String modulePath

    public HttpArtifact(HttpServer server, String modulePath) {
        super(server)
        this.modulePath = modulePath
    }

    void expectHeadMissing() {
        server.expectHeadMissing(path)
    }

    void expectGetMissing() {
        server.expectGetMissing(path)
    }

    HttpResource getMd5(){
        return new BasicHttpResource(server, getMd5File(), "${path}.md5")
    }

    HttpResource getSha1() {
        return new BasicHttpResource(server, getSha1File(), "${path}.sha1")
    }

    protected String getPath() {
        "${modulePath}/${file.name}"
    }

    protected abstract File getSha1File();

    protected abstract File getMd5File();

    abstract TestFile getFile();
}
