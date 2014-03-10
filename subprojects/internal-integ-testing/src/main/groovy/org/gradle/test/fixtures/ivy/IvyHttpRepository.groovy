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

package org.gradle.test.fixtures.ivy

import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.server.http.HttpServer

class IvyHttpRepository implements IvyRepository, HttpRepository {
    private final HttpServer server
    private final IvyFileRepository backingRepository
    private final String contextPath

    IvyHttpRepository(HttpServer server, String contextPath = "/repo", IvyFileRepository backingRepository) {
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

    String getIvyPattern() {
        return "$uri/${backingRepository.baseIvyPattern}"
    }

    String getArtifactPattern() {
        return "$uri/${backingRepository.baseArtifactPattern}"
    }

    void allowDirectoryListGet(String organisation, String module) {
        server.allowGetDirectoryListing("$contextPath/$organisation/$module/", backingRepository.module(organisation, module, "1.0").moduleDir.parentFile)
    }

    void expectDirectoryListGet(String organisation, String module) {
        server.expectGetDirectoryListing("$contextPath/$organisation/$module/", backingRepository.module(organisation, module, "1.0").moduleDir.parentFile)
    }

    void expectDirectoryListGetMissing(String organisation, String module) {
        server.expectGetMissing("$contextPath/$organisation/$module/")
    }

    void expectDirectoryListGetBroken(String organisation, String module) {
        server.expectGetBroken("$contextPath/$organisation/$module/")
    }

    IvyHttpModule module(String organisation, String module, Object revision = "1.0") {
        return new IvyHttpModule(this, server, "$contextPath/$organisation/$module/$revision", backingRepository.module(organisation, module, revision))
    }
}
