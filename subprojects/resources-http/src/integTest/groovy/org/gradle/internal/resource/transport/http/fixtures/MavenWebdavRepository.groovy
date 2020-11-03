/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.resource.transport.http.fixtures

import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository

/**
 * A fixture for dealing with remote HTTP Maven repositories.
 */
class MavenWebdavRepository implements MavenRepository {

    private final WebdavServer server

    private final MavenFileRepository backingRepository
    private final String contextPath
    private final HttpRepository.MetadataType metadataType

    MavenWebdavRepository(WebdavServer server, String contextPath = "/repo", HttpRepository.MetadataType metadataType = HttpRepository.MetadataType.DEFAULT) {
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Context path must start with '/'")
        }
        this.contextPath = contextPath
        this.server = server
        this.metadataType = metadataType
        this.backingRepository = new MavenFileRepository(new TestFile(server.repositoryDirectory, contextPath))
    }

    URI getUri() {
        return new URI("dav+https://localhost:${server.sslPort}${contextPath}")
    }

    MavenModule module(String groupId, String artifactId) {
        return module(groupId, artifactId, "1.0")
    }

    MavenModule module(String groupId, String artifactId, String version) {
        return backingRepository.module(groupId, artifactId, version)
    }
}
