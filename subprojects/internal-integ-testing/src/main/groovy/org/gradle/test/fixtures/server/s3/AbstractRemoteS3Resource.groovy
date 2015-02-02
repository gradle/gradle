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

abstract class AbstractRemoteS3Resource implements MavenRemoteResource {
    S3StubServer server
    S3StubSupport s3StubSupport
    String bucket
    String repositoryPath
    File baseDir
    String filePath

    AbstractRemoteS3Resource(S3StubServer server, File baseDir, String bucket, String repositoryPath, String filePath) {
        this.filePath = filePath
        this.baseDir = baseDir
        this.bucket = bucket
        this.server = server
        this.repositoryPath = repositoryPath
        this.s3StubSupport = new S3StubSupport(server)
    }

    abstract String fileNamePattern(String fileName)

    @Override
    void expectUpload() {
        s3StubSupport.stubPutFile(path(fileNamePattern(filePath)))
    }

    @Override
    void expectDownload() {
        s3StubSupport.stubGetFile(path(fileNamePattern(filePath)))
    }

    @Override
    void expectMetadataRetrieve() {
        s3StubSupport.stubMetaData(path(filePath))
    }

    @Override
    void expectSha1Upload() {
        s3StubSupport.stubPutFile(path(fileNamePattern("${filePath}.sha1")))
    }

    @Override
    void expectMd5Upload() {
        s3StubSupport.stubPutFile(path(fileNamePattern("${filePath}.md5")))
    }

    @Override
    void expectSha1Download() {
        s3StubSupport.stubGetFile(path(fileNamePattern("${filePath}.sha1")))
    }

    @Override
    void expectMd5Download() {
        s3StubSupport.stubGetFile(path(fileNamePattern("${filePath}.md5")))
    }

    @Override
    void expectUploadAccessDenied() {
        s3StubSupport.stubPutFileAuthFailure(path(fileNamePattern(filePath)))
    }

    def path(String relFilePath) {
        "/${bucket}$repositoryPath/$relFilePath".replaceAll('//', '/')
    }
}
