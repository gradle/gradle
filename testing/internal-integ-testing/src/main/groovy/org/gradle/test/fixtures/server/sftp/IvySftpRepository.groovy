/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.server.sftp

import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.RemoteIvyRepository

class IvySftpRepository implements RemoteIvyRepository {

    private final SFTPServer server
    private final IvyFileRepository backingRepository
    private final String contextPath

    IvySftpRepository(SFTPServer server, String contextPath, boolean m2Compatible = false, String dirPattern = null, String ivyFilePattern = null, String artifactFilePattern = null) {
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Context path must start with '/'")
        }
        this.contextPath = contextPath
        this.server = server
        this.backingRepository = new IvyFileRepository(server.file(contextPath.substring(1)), m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    URI getUri() {
        return new URI("${serverUri}${contextPath}")
    }

    URI getServerUri() {
        server.uri
    }

    String getIvyPattern() {
        return "$uri/${backingRepository.baseIvyPattern}"
    }

    String getArtifactPattern() {
        return "$uri/${backingRepository.baseArtifactPattern}"
    }

    String getBaseIvyPattern() {
        return backingRepository.baseIvyPattern
    }

    String getBaseArtifactPattern() {
        return backingRepository.baseArtifactPattern
    }

    @Override
    SftpDirectoryResource directoryList(String organisation, String module) {
        return new SftpDirectoryResource(server, backingRepository.moduleDir(organisation, module))
    }

    IvySftpModule module(String organisation, String module, String revision = "1.0") {
        return new IvySftpModule(this, server, backingRepository.module(organisation, module, revision))
    }
}
