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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.resource.RemoteResource

class S3Resource implements RemoteResource {
    S3Server server
    TestFile file
    String bucket
    String repositoryPath

    S3Resource(S3Server server, TestFile file, String repositoryPath, String bucket) {
        this.repositoryPath = repositoryPath
        this.bucket = bucket
        this.server = server
        this.file = file
    }

    String getPath() {
        return repositoryPath
    }

    @Override
    URI getUri() {
        return new URI("s3:/${relativeFilePath()}")
    }

    @Override
    void expectDownload() {
        server.stubGetFile(file, relativeFilePath())
    }

    @Override
    void expectDownloadMissing() {
        def path = relativeFilePath()
        server.stubFileNotFound(path)
    }

    void expectDownloadAuthenticationError() {
        server.stubGetFileAuthFailure(relativeFilePath())
    }

    @Override
    void expectMetadataRetrieve() {
        server.stubMetaData(file, relativeFilePath())
    }

    @Override
    void expectMetadataRetrieveMissing() {
        server.stubMetaDataMissing(relativeFilePath())
    }

    @Override
    void expectDownloadBroken() {
        server.stubGetFileBroken(relativeFilePath())
    }

    @Override
    void expectParentMkdir() {
        // Not required
    }

    @Override
    void expectParentCheckdir() {
        // Not required
    }

    void expectUpload() {
        server.stubPutFile(file, relativeFilePath())
    }

    @Override
    void expectUploadBroken() {
        server.stubPutFileAuthFailure(relativeFilePath())
    }

    @Override
    void expectMetadataRetrieveBroken() {
        server.stubMetaDataBroken(relativeFilePath())
    }

    def relativeFilePath() {
        String absolute = file.toURI()
        String base = "/${bucket}$repositoryPath"
        absolute.substring(absolute.indexOf(base), absolute.length())
    }

    def expectPutAuthenticationError() {
        server.stubPutFileAuthFailure(relativeFilePath());
    }
}
