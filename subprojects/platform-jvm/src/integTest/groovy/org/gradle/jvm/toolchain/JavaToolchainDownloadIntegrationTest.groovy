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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.internal.jvm.Jvm
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.hamcrest.CoreMatchers
import org.junit.Rule

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class JavaToolchainDownloadIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    BlockingHttpServer server = new BlockingHttpServer()

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def "can properly fails for missing combination"() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching these requirements: {languageVersion=99, vendor=any, implementation=vendor-specific}")
            .assertHasCause("Unable to download toolchain. This might indicate that the combination (version, architecture, release/early access, ...) for the requested JDK is not available.")
            .assertThatCause(CoreMatchers.startsWith("Unable to download external resource https://api.adoptopenjdk.net/v3/binary/latest/99/ga"))
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain selection that requires downloading fails when it is disabled'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                }
            }
        """

        propertiesFile << """
            org.gradle.java.installations.auto-download=false
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("No compatible toolchains found for request filter: {languageVersion=14, vendor=any, implementation=vendor-specific} (auto-detect false, auto-download false)")
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'toolchain download on http fails'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }
        """

        propertiesFile << """
            org.gradle.jvm.toolchain.install.adoptopenjdk.baseUri=http://example.com
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause('Unable to download toolchain matching these requirements: {languageVersion=99, vendor=any, implementation=vendor-specific}')
            .assertThatCause(CoreMatchers.startsWith('Attempting to download a resource from an insecure URI http://example.com'))
    }

    @ToBeFixedForConfigurationCache(because = "Fails the build with an additional error")
    def 'can provide a custom provisioning service'() {
        buildFile << """
            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(99)
                }
            }

            interface Services {
                @javax.inject.Inject
                JavaToolchainService getToolchains()
            }

            def toolchains = objects.newInstance(Services).toolchains
            toolchains.registerToolchainProvisioningService(Custom)

            abstract class Custom implements JavaToolchainProvisioningService {
                void findCandidates(JavaToolchainProvisioningDetails details) {
                    details.listCandidates([
                        details.newCandidate()
                            .withVendor("foo")
                            .withLanguageVersion(99)
                            .build()
                    ])
                }

                LazyProvisioner provisionerFor(JavaToolchainCandidate candidate) {
                    new LazyProvisioner() {
                        String getFileName() { "dummy.zip" }
                        boolean provision(File destination) {
                            throw new RuntimeException("Provisioning custom JDK")
                        }
                    }
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        failure = executer
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDownloadEnabled()
            .runWithFailure()

        then:
        failure.assertHasDescription("Execution failed for task ':compileJava'.")
            .assertHasCause("Failed to calculate the value of task ':compileJava' property 'javaCompiler'")
            .assertHasCause("Unable to download toolchain matching these requirements: {languageVersion=99, vendor=any, implementation=vendor-specific}")
            .assertHasCause("Provisioning custom JDK")
    }

    @ToBeFixedForConfigurationCache(because = "Does not support serializing toolchain")
    def 'can provide a custom provisioning service using an external resource service'() {
        server.start()

        buildFile """import java.util.zip.*

            apply plugin: "java"

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${JavaVersion.current().majorVersion})
                    vendor = JvmVendorSpec.matching("custom")
                }
            }

            interface Services {
                @javax.inject.Inject
                JavaToolchainService getToolchains()


            }
            def services = objects.newInstance(Services)
            def toolchains = services.toolchains

            toolchains.registerToolchainProvisioningService(Custom)

            abstract class Custom implements JavaToolchainProvisioningService {

                @javax.inject.Inject
                abstract RemoteResourceService getRemoteResourceService()

                void findCandidates(JavaToolchainProvisioningDetails details) {
                    println "Listing candidates"
                    def candidates = []
                    remoteResourceService.withResource("${server.uri}/list.txt".toURI(), "Listing") {
                        it.withContent { stream ->
                            stream.withReader { reader ->
                                def (vendor, version)  = reader.readLine().split(' ')
                                candidates << details.newCandidate()
                                    .withVendor(vendor)
                                    .withLanguageVersion(Integer.valueOf(version))
                                    .build()
                            }
                        }
                    }
                    println "Found \${candidates.size()} candidates"
                    details.listCandidates(candidates)
                }

                LazyProvisioner provisionerFor(JavaToolchainCandidate candidate) {
                    new LazyProvisioner() {
                        String getFileName() { "jdk.tar.gz" }
                        boolean provision(File destination) {
                            remoteResourceService.withResource("${server.uri}/jdk.tar.gz".toURI(), "Dummy JDK") {
                                println("Persisting archive into \$destination")
                                it.persistInto(destination)
                            }
                            true
                        }
                    }
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        when:
        def listOfCandidates = server.expectAndBlock(server.get("list.txt").send("custom ${JavaVersion.current().majorVersion}"))
        def jdkRequest = server.expectAndBlock(server.get("jdk.tar.gz").sendFile(withJdkArchive()))
        def build = executer
            .withArgument("-Dorg.gradle.internal.remote.resource.allow.insecure.protocol=true")
            .withTasks("compileJava")
            .requireOwnGradleUserHomeDir()
            .withToolchainDownloadEnabled()
            .start()
        listOfCandidates.waitForAllPendingCalls()
        listOfCandidates.releaseAll()
        jdkRequest.waitForAllPendingCalls()
        jdkRequest.releaseAll()
        result = build.waitForFinish()

        then:
        outputContains("Listing candidates")
        result.assertTasksExecuted(":compileJava")

        when:
        // we use head + get because the fixture doesn't seem to support sending a head request which
        // tells that the resource is the same
        def head = server.expectAndBlock(server.head("list.txt"))
        def get = server.expectAndBlock(server.get("list.txt").send("custom ${JavaVersion.current().majorVersion}"))

        build = executer.withToolchainDownloadEnabled()
            .withArgument("--rerun-tasks")
            .withTasks("compileJava")
            .start()
        head.waitForAllPendingCalls()
        head.releaseAll()
        get.waitForAllPendingCalls()
        get.releaseAll()
        result = build.waitForFinish()

        then: "uses toolchains from cache"
        result.assertTasksExecuted(":compileJava")
    }

    /**
     * Builds an archive of the current JDK so that it can be served in toolchain
     * provisioning tests
     * @return the JDK archive
     */
    File withJdkArchive() {
        File outputFile = temporaryFolder.createFile("jdk.tar.gz")
        def root = Jvm.current().javaHome.parentFile.toPath()
        println("Building JDK archive for tests: $root into $outputFile")
        try (def archiveOut = new TarArchiveOutputStream(new GzipCompressorOutputStream(Files.newOutputStream(outputFile.toPath())))) {
            archiveOut.longFileMode = TarArchiveOutputStream.LONGFILE_POSIX
            int cpt = 0
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    // We're creating a parent directory in the archive
                    if (dir.parent == root && dir.toFile().name != Jvm.current().javaHome.name) {
                        return FileVisitResult.SKIP_SUBTREE
                    }
                    String targetFile = "${root.relativize(dir)}/"
                    if (targetFile != "/") {
                        def source = dir.toFile()
                        def tarEntry = new TarArchiveEntry(source, targetFile)
                        archiveOut.putArchiveEntry(tarEntry)
                        archiveOut.closeArchiveEntry()
                    }
                    return FileVisitResult.CONTINUE
                }


                @Override
                FileVisitResult visitFile(Path file,
                                          BasicFileAttributes attributes) {

                    if (attributes.isSymbolicLink()) {
                        return FileVisitResult.CONTINUE
                    }
                    if ((cpt++ % 10) == 0) {
                        print "."
                        System.out.flush()
                    }
                    def targetFile = root.relativize(file)
                    def source = file.toFile()
                    def tarEntry = new TarArchiveEntry(source, targetFile.toString())
                    if (source.canExecute()) {
                        tarEntry.mode = 0777
                    }
                    archiveOut.putArchiveEntry(tarEntry)
                    Files.copy(file, archiveOut)
                    archiveOut.closeArchiveEntry()
                    FileVisitResult.CONTINUE
                }
            })
            println()
            archiveOut.finish()
        }
        outputFile
    }
}
