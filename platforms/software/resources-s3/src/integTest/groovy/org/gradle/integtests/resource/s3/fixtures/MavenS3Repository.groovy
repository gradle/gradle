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


package org.gradle.integtests.resource.s3.fixtures

import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenRepository

class MavenS3Repository implements MavenRepository {
    private final S3Server server
    private final MavenFileRepository backingRepository
    private final String bucket
    private final String repositoryPath

    MavenS3Repository(S3Server server, File repoDir, String repositoryPath, String bucket) {
        assert !bucket.startsWith('/')
        this.server = server
        this.bucket = bucket
        this.backingRepository = new MavenFileRepository(repoDir.file(bucket + repositoryPath))
        this.repositoryPath = repositoryPath
    }

    URI getUri() {
        new URI("s3://${bucket}${repositoryPath}")
    }

    MavenS3Module module(String organisation, String module, String revision = "1.0") {
        new MavenS3Module(server, backingRepository.module(organisation, module, revision), repositoryPath, bucket)
    }
}
