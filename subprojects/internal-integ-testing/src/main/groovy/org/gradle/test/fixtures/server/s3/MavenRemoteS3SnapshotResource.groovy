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


package org.gradle.test.fixtures.server.s3

import org.gradle.test.fixtures.maven.MavenRemoteResource

class MavenRemoteS3SnapshotResource extends AbstractRemoteS3Resource implements MavenRemoteResource {
    String snapshotPattern = "(\\d{8})\\.(\\d{6})-(\\d+)"

    MavenRemoteS3SnapshotResource(S3StubServer server, File baseDir, String bucket, String repositoryPath, String filePath) {
        super(server, baseDir, bucket, repositoryPath, filePath)
    }

    @Override
    String fileNamePattern(String fileName) {
        return toSnapshotPattern(fileName)
    }

    String toSnapshotPattern(String fileName) {
        String artifactFile = fileName.substring(fileName.lastIndexOf('/') + 1, fileName.length())
        def patternedArtifactFile = artifactFile.replace('SNAPSHOT', snapshotPattern)
        def fileMatchingPattern = fileName.replace(artifactFile, patternedArtifactFile)
        return fileMatchingPattern
    }
}
