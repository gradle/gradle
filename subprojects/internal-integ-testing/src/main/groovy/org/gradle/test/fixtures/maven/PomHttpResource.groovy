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

class PomHttpResource extends BasicHttpResource {
    MavenFileModule backingModule

    PomHttpResource(HttpServer httpServer, String path, MavenFileModule backingModule) {
        super(httpServer, backingModule.pomFile, path)
        this.backingModule = backingModule
    }

    @Override
    void expectGetMissing() {
        server.expectGetMissing(path - getFile().name + getMissingPomName());
    }

    private String getMissingPomName() {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}.pom"
        } else {
            return getFile().name
        }
    }

    HttpResource getMd5() {
        return new BasicHttpResource(server, backingModule.getMd5File(getFile()), "${path}.md5")
    }

    HttpResource getSha1() {
        return new BasicHttpResource(server, backingModule.getSha1File(getFile()), "${path}.sha1")
    }
}
