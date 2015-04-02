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

package org.gradle.test.fixtures.server.sftp

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.resource.RemoteResource
import org.gradle.util.GFileUtils

class SftpDirectoryResource implements RemoteResource {

    SFTPServer server
    TestFile file

    SftpDirectoryResource(SFTPServer server, TestFile file) {
        this.server = server
        this.file = file
    }

    String getPathOnServer() {
        return "/${GFileUtils.relativePath(server.baseDir, file)}/"
    }

    URI getUri() {
        return new URI("${server.uri}${pathOnServer}")
    }

    void expectMetadataRetrieveBroken() {
        throw new UnsupportedOperationException()
    }

    void expectMetadataRetrieveMissing() {
        throw new UnsupportedOperationException()
    }

    void expectDownload() {
        server.expectStat(pathOnServer)
        server.expectDirectoryList(pathOnServer)
    }

    void expectDownloadMissing() {
        throw new UnsupportedOperationException()
    }

    void expectMetadataRetrieve() {
        throw new UnsupportedOperationException()
    }

    void expectDownloadBroken() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectParentMkdir() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectParentCheckdir() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectUpload() {
        throw new UnsupportedOperationException()
    }

    @Override
    void expectUploadBroken() {
        throw new UnsupportedOperationException()
    }
}
