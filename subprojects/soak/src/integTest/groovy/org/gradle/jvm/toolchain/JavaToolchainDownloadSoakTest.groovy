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

package org.gradle.jvm.toolchain


import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.JavaVersion
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.nativeintegration.services.FileSystems
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.internal.RelativePathUtil

import java.nio.file.Files

class JavaToolchainDownloadSoakTest extends AbstractIntegrationSpec {

    public static final JavaVersion JAVA_VERSION = JavaVersion.VERSION_17

    private static final String getToolchainResolverSection(String uri) {
        return """
            ${JavaToolchainDownloadUtil.applyToolchainResolverPlugin(JavaToolchainDownloadUtil.singleUrlResolverCode(uri))}
            toolchainManagement {
                jvm {
                    javaRepositories {
                        repository('custom') {
                            resolverClass = CustomToolchainResolver
                        }
                    }
                }
            }
    """.stripIndent()
    }

    public static final String TOOLCHAIN_WITH_VERSION = """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($JAVA_VERSION)
                }
            }
        """

    JdkRepository jdkRepository

    def setup() {
        jdkRepository = new JdkRepository()
        jdkRepository.expectHead()
        jdkRepository.expectGet()
        def uri = jdkRepository.start()

        settingsFile << getToolchainResolverSection(uri.toString())

        buildFile << """
            plugins {
                id "java"
            }

            $TOOLCHAIN_WITH_VERSION
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        executer.requireOwnGradleUserHomeDir()
                .withToolchainDownloadEnabled()
    }

    def "can download missing jdk automatically"() {
        when:
        result = executer
                .withTasks("compileJava")
                .run()

        then:
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded()
    }

    def "clean destination folder when downloading toolchain"() {
        when: "build runs and doesn't have a local JDK to use for compilation"
        result = executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
                .run()

        then: "suitable JDK gets auto-provisioned"
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded()

        when: "the marker file of the auto-provisioned JDK is deleted, making the JDK not detectable"
        //delete marker file to make the previously downloaded installation undetectable
        def markerFile = findMarkerFile(executer.gradleUserHomeDir.file("jdks"))
        markerFile.delete()
        assert !markerFile.exists()

        and: "build runs again"
        jdkRepository.expectHead()
        executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false", "-Porg.gradle.java.installations.auto-download=true")
                .run()

        then: "the JDK is auto-provisioned again and its files, even though they are already there don't trigger an error, they just get overwritten"
        markerFile.exists()
    }

    def "issue warning on using auto-provisioned toolchain with no configured repositories"() {
        when: "build runs and doesn't have a local JDK to use for compilation"
        result = executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
                .run()

        then: "suitable JDK gets auto-provisioned"
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded()

        when: "build has no toolchain repositories configured"
        settingsFile.text = ''

        then: "build runs again, uses previously auto-provisioned toolchain and warns about toolchain repositories not being configured"
        executer
                .expectDocumentedDeprecationWarning("Using a toolchain installed via auto-provisioning, but having no toolchain repositories configured. " +
                        "This behavior is deprecated. Consider defining toolchain download repositories, otherwise the build might fail in clean environments; " +
                        "see https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories")
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false", "-Porg.gradle.java.installations.auto-download=true")
                .run()
    }

    private void assertJdkWasDownloaded(String implementation = null) {
        assert executer.gradleUserHomeDir.file("jdks").listFiles({ file ->
            file.name.contains("-$JAVA_VERSION-") && (implementation == null || file.name.contains(implementation))
        } as FileFilter)
    }

    def cleanup() {
        executer.gradleUserHomeDir.file("jdks").deleteDir()
        jdkRepository.stop()
    }

    private static File findMarkerFile(File directory) {
        File markerFile
        new SingleIncludePatternFileTree(directory, "**").visit(new FileVisitor() {
            @Override
            void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            void visitFile(FileVisitDetails fileDetails) {
                if (fileDetails.file.name == "provisioned.ok") {
                    markerFile = fileDetails.file
                }
            }
        })
        if (markerFile == null) {
            throw new RuntimeException("Marker file not found in " + directory.getAbsolutePath() + "")
        }
        return markerFile
    }

    private static class JdkRepository {

        private static final String ARCHIVE_NAME = "jdk.zip"

        private BlockingHttpServer server

        private Jvm jdk

        JdkRepository() {
            jdk = AvailableJavaHomes.getJdk(JAVA_VERSION)
            assert jdk != null

            server = new BlockingHttpServer(1000)
        }

        URI start() {
            server.start()
            server.uri(ARCHIVE_NAME)
        }

        void expectHead() {
            server.expect(server.head(ARCHIVE_NAME))
        }

        void expectGet() {
            server.expect(server.get(ARCHIVE_NAME, { e ->
                e.sendResponseHeaders(200, 0)
                zip(jdk.javaHome, e.getResponseBody())
            }))
        }

        void stop() {
            server.stop()
        }

        private static void zip(File jdkHomeDirectory, OutputStream outputStream) {
            try (ArchiveOutputStream aos = new ZipArchiveOutputStream(outputStream)) {
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

}
