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

import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.server.http.HttpServer

/**
 * A fixture for dealing with remote HTTP Maven repositories.
 */
class MavenHttpRepository implements MavenRepository, HttpRepository {
    private final HttpServer server
    private final MavenFileRepository backingRepository
    private final String contextPath

    MavenHttpRepository(HttpServer server, String contextPath = "/repo", MavenFileRepository backingRepository) {
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Context path must start with '/'")
        }
        this.contextPath = contextPath
        this.server = server
        this.backingRepository = backingRepository
    }

    URI getUri() {
        return new URI("http://localhost:${server.port}${contextPath}")
    }

    HttpResource getModuleMetaData(String groupId, String artifactId) {
        return module(groupId, artifactId).rootMetaData
    }

    void expectDirectoryListGet(String groupId, String artifactId) {
        def path = "${groupId.replace('.', '/')}/$artifactId/"
        server.expectGetDirectoryListing("$contextPath/$path", backingRepository.getRootDir().file(path))
    }

    MavenHttpModule module(String groupId, String artifactId) {
        return module(groupId, artifactId, "1.0")
    }

    MavenHttpModule module(String groupId, String artifactId, Object version) {
        def backingModule = backingRepository.module(groupId, artifactId, version)
        return new MavenHttpModule(server, contextPath, backingModule)
    }
}
