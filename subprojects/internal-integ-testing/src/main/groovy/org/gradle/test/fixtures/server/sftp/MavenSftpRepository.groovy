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

package org.gradle.test.fixtures.server.sftp

import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenRepository

class MavenSftpRepository implements MavenRepository {
    private final SFTPServer server
    private final MavenFileRepository backingRepository
    private final String contextPath

    MavenSftpRepository(SFTPServer server, String contextPath) {
        this.server = server
        this.backingRepository = new MavenFileRepository(server.file(contextPath.substring(1)))
        this.contextPath = contextPath
    }

    URI getUri() {
        new URI("sftp://${server.hostAddress}:${server.port}${contextPath}")
    }

    MavenSftpModule module(String organisation, String module, String revision = "1.0") {
        new MavenSftpModule(server, backingRepository.module(organisation, module, revision))
    }
}
