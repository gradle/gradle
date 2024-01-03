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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.RemoteIvyRepository

class IvyGcsRepository implements RemoteIvyRepository {

    GcsServer server
    String bucket
    IvyFileRepository backingRepository
    String repositoryPath

    IvyGcsRepository(GcsServer server, TestFile repoDir, String repositoryPath, String bucket, boolean m2Compatible = false, String dirPattern = null, String ivyFilePattern = null, String artifactFilePattern = null) {
        assert !bucket.startsWith('/')
        this.server = server
        this.bucket = bucket
        this.backingRepository = new IvyFileRepository(repoDir, m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
        this.repositoryPath = repositoryPath
    }

    URI getUri() {
        new URI("gcs://${bucket}${repositoryPath}")
    }

    @Override
    String getIvyPattern() {
        return "$uri/${backingRepository.baseIvyPattern}"
    }

    @Override
    IvyGcsModule module(String organisation, String module) {
        return new IvyGcsModule(server, backingRepository.module(organisation, module), repositoryPath, bucket)
    }

    @Override
    IvyGcsModule module(String organisation, String module, String version) {
        return new IvyGcsModule(server, backingRepository.module(organisation, module, version), repositoryPath, bucket)
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

    GcsDirectoryResource directoryList(String organisation, String module) {
        return new GcsDirectoryResource(server, bucket, backingRepository.moduleDir(organisation, module))
    }
}
