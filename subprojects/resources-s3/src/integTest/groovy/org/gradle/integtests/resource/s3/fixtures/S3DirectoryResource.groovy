/*
 * Copyright 2015 the original author or authors.
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

class S3DirectoryResource implements RemoteResource {

    private final S3Server server
    private final TestFile directory
    private String bucket
    private final String path

    S3DirectoryResource(S3Server server, String bucket, TestFile directory) {
        this.bucket = bucket
        this.directory = directory
        this.server = server
        def directoryUri = directory.toURI().toString()
        this.path = directoryUri.substring(directoryUri.indexOf(bucket) + bucket.length() + 1)
    }

    URI getUri() {
        return new URI("s3", bucket, path, null, null)
    }

    @Override
    void expectDownload() {
        expectGet()
    }

    @Override
    void expectDownloadBroken() {
        expectGetBroken()
    }

    @Override
    void expectDownloadMissing() {
        expectGetMissing()
    }

    @Override
    void expectMetadataRetrieve() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectMetadataRetrieveMissing() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectMetadataRetrieveBroken() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectParentMkdir() {
        // Not required
    }

    @Override
    void expectParentCheckdir() {
        // Not required
    }

    @Override
    void expectUpload() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectUploadBroken() {
        throw new UnsupportedOperationException()
    }

    public void expectGet() {
        server.stubListFile(directory, bucket, path)
    }

    public void expectGetMissing() {
        server.expectGetMissing("/$bucket")
    }

    public void expectGetBroken() {
        server.expectGetBroken("/$bucket")
    }
}
