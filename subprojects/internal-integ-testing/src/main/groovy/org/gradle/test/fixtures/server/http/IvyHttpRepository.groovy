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

import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.RemoteIvyRepository

class IvyHttpRepository implements RemoteIvyRepository, HttpRepository {
    private final HttpServer server
    private final IvyFileRepository backingRepository
    private final String contextPath
    private final boolean m2Compatible

    IvyHttpRepository(HttpServer server, String contextPath = "/repo", IvyFileRepository backingRepository, boolean m2Compatible = false) {
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Context path must start with '/'")
        }
        this.server = server
        this.contextPath = contextPath
        this.backingRepository = backingRepository
        this.m2Compatible = m2Compatible
    }

    URI getUri() {
        return new URI("${server.uri}${contextPath}")
    }

    String getIvyPattern() {
        return "$uri/${backingRepository.baseIvyPattern}"
    }

    String getArtifactPattern() {
        return "$uri/${backingRepository.baseArtifactPattern}"
    }

    HttpDirectoryResource directoryList(String organisation, String module) {
        return new HttpDirectoryResource(server, "$contextPath/$organisation/$module/", backingRepository.moduleDir(organisation, module))
    }

    IvyHttpModule module(String organisation, String module, String revision = "1.0") {
        def path = backingRepository.getDirPath(organisation, module, revision.toString())
        return new IvyHttpModule(this, server, contextPath, "$contextPath/$path", backingRepository.module(organisation, module, revision))
    }

    String getBaseIvyPattern() {
        backingRepository.baseIvyPattern
    }

    String getBaseArtifactPattern() {
        backingRepository.baseArtifactPattern
    }
}
