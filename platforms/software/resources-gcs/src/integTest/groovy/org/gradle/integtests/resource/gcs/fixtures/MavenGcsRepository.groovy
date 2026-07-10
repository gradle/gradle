/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resource.gcs.fixtures

import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenRepository

class MavenGcsRepository implements MavenRepository {
    private final GcsServer server
    private final MavenFileRepository backingRepository
    private final String bucket
    private final String repositoryPath

    MavenGcsRepository(GcsServer server, File repoDir, String repositoryPath, String bucket) {
        assert !bucket.startsWith('/')
        this.server = server
        this.bucket = bucket
        this.backingRepository = new MavenFileRepository(repoDir.file(bucket + repositoryPath))
        this.repositoryPath = repositoryPath
    }

    URI getUri() {
        new URI("gcs://${bucket}${repositoryPath}")
    }

    @Override
    MavenGcsModule module(String organisation, String module, String version = "1.0") {
        new MavenGcsModule(server, backingRepository.module(organisation, module, version), repositoryPath, bucket)
    }

    GcsDirectoryResource directoryList(String organisation, String module) {
        return new GcsDirectoryResource(server, bucket, this.module(organisation, module).backingModule.moduleDir.parentFile)
    }
}
