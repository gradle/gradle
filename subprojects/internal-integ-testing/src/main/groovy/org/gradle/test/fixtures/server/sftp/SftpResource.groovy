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

import org.apache.commons.io.FilenameUtils
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.resource.RemoteResource
import org.gradle.util.GFileUtils

class SftpResource implements RemoteResource {

    SFTPServer server
    TestFile file

    SftpResource(SFTPServer server, TestFile file) {
        this.server = server
        this.file = file
    }

    String getPathOnServer() {
        return "/${GFileUtils.relativePath(server.baseDir, file)}"
    }

    URI getUri() {
        return new URI("${server.uri}${pathOnServer}")
    }

    void expectLstat() {
        server.expectLstat(pathOnServer)
    }

    void expectStat() {
        server.expectStat(pathOnServer)
    }

    void expectOpen() {
        server.expectOpen(pathOnServer)
    }

    def allowWrite() {
        server.allowWrite(pathOnServer)
    }

    void expectClose() {
        server.expectClose(pathOnServer)
    }

    void expectFileDownload() {
        // TODO - should not do this stat request
        server.expectStat(pathOnServer)
        server.expectFileDownload(pathOnServer)
    }

    void expectFileAndSha1Upload() {
        server.expectFileUpload(pathOnServer)
        server.expectFileUpload("${pathOnServer}.sha1")
    }

    void expectMetadataRetrieveBroken() {
        server.expectMetadataRetrieveBroken(pathOnServer)
    }

    void expectMetadataRetrieveMissing() {
        server.expectLstatMissing(pathOnServer)
    }

    void expectMkdirs() {
        def directory = FilenameUtils.getFullPathNoEndSeparator(pathOnServer)
        withEachDirectory { String path ->
            if (path != directory) {
                server.expectLstat(path)
            }
            server.expectMkdir(path)
        }
    }

    void withEachDirectory(Closure action) {
        def directory = FilenameUtils.getFullPathNoEndSeparator(pathOnServer)
        directory.tokenize('/').findAll().inject('') { path, token ->
            def currentPath = "$path/$token"
            action(currentPath)
            currentPath
        }
    }

    void expectWriteBroken() {
        server.expectWriteBroken(pathOnServer)
    }

    void expectDownload() {
        expectMetadataRetrieve()
        expectFileDownload()
    }

    void expectDownloadMissing() {
        expectMetadataRetrieveMissing()
    }

    void expectMetadataRetrieve() {
        expectLstat()
    }

    void expectDownloadBroken() {
        expectMetadataRetrieveBroken()
    }
}
