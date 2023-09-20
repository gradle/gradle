/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.jvm.toolchain

import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeintegration.services.FileSystems
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.internal.RelativePathUtil

import java.nio.file.Files


class JdkRepository {

    private BlockingHttpServer server

    private Jvm jdk

    private String archiveName
    private File archiveFile

    JdkRepository(JavaVersion javaVersion) {
        this(Objects.requireNonNull(AvailableJavaHomes.getJdk(javaVersion)), "jdk.zip")
    }

    JdkRepository(Jvm jdk, String archiveName) {
        this.jdk = jdk
        this.archiveName = archiveName

        archiveFile = zip(jdk.javaHome, File.createTempFile(archiveName, ".tmp").with {deleteOnExit(); it })
        server = new BlockingHttpServer(1000)
    }

    URI start() {
        server.start()
        server.uri(archiveName)
    }

    void reset() {
        expectHead()
        expectGet()
    }

    void expectHead() {
        server.expect(server.head(archiveName))
    }

    void expectGet() {
        server.expect(server.get(archiveName, { e ->
            e.sendResponseHeaders(200, 0)
            IOUtils.copy(archiveFile, e.getResponseBody())
        }))
    }

    void stop() {
        server.stop()
        archiveFile.delete()
    }

    Jvm getJdk() {
        jdk
    }

    private static File zip(File jdkHomeDirectory, File outputFile) {
        try (ArchiveOutputStream aos = new ZipArchiveOutputStream(new FileOutputStream(outputFile))) {
            def jdkHomeParentDirectory = jdkHomeDirectory.getParentFile()
            List<File> jdkFiles = new LinkedList<>()
            populateFilesList(jdkFiles, jdkHomeDirectory)

            def fileSystem = FileSystems.getDefault()

            for (File file : jdkFiles) {
                def path = RelativePathUtil.relativePath(jdkHomeParentDirectory, file)
                ZipArchiveEntry entry = (ZipArchiveEntry) aos.createArchiveEntry(file, path)
                entry.setUnixMode(fileSystem.getUnixMode(file))
                aos.putArchiveEntry(entry)
                if (file.isFile()) {
                    try (InputStream i = Files.newInputStream(file.toPath())) {
                        IOUtils.copy(i, aos)
                    }
                }
                aos.closeArchiveEntry()
            }
            aos.finish()
        }
        outputFile
    }

    private static void populateFilesList(List<File> fileList, File dir) throws IOException {
        File[] files = dir.listFiles()
        for (File file : files) {
            if (file.isFile()) {
                fileList.add(file)
            } else {
                populateFilesList(fileList, file)
            }
        }
    }
}
