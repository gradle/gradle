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


import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.singleUrlResolverCode

@Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
class JavaToolchainDownloadSoakTest extends AbstractIntegrationSpec {
    static final Jvm EXPECTED_JVM = AvailableJavaHomes.differentVersion

    static JdkRepository jdkRepository

    static URI uri

    def setupSpec() {
        jdkRepository = new JdkRepository(EXPECTED_JVM.javaVersion)
        uri = jdkRepository.start()
    }

    def cleanupSpec() {
        jdkRepository.stop()
    }

    def setup() {
        jdkRepository.reset()

        settingsFile << """
            ${applyToolchainResolverPlugin("CustomToolchainResolver", singleUrlResolverCode(uri))}
        """

        buildFile << """
            plugins {
                id "java"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${EXPECTED_JVM.javaVersion.majorVersion})
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        executer.requireOwnGradleUserHomeDir("needs to test toolchain download functionality")
                .withToolchainDownloadEnabled()
    }

    def cleanup() {
        executer.gradleUserHomeDir.file("jdks").deleteDir()
    }

    def "can download missing jdk automatically"() {
        when:
        succeeds("compileJava")
        then:
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded()
    }

    def "clean destination folder when downloading toolchain"() {
        when: "build runs and doesn't have a local JDK to use for compilation"
        succeeds("compileJava", "-Porg.gradle.java.installations.auto-detect=false")

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
        succeeds("compileJava", "-Porg.gradle.java.installations.auto-detect=false", "-Porg.gradle.java.installations.auto-download=true")

        then: "the JDK is auto-provisioned again and its files, even though they are already there don't trigger an error, they just get overwritten"
        markerFile.exists()
    }

    def "issue warning on using auto-provisioned toolchain with no configured repositories"() {
        when: "build runs and doesn't have a local JDK to use for compilation"
        succeeds("compileJava", "-Porg.gradle.java.installations.auto-detect=false")

        then: "suitable JDK gets auto-provisioned"
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded()

        when: "build has no toolchain repositories configured"
        settingsFile.text = ''

        then: "build runs again, uses previously auto-provisioned toolchain and warns about toolchain repositories not being configured"
        def toolchainName = AvailableJavaHomes.getJvmInstallationMetadata(EXPECTED_JVM).displayName
        executer.expectDocumentedDeprecationWarning("Using toolchain '${toolchainName}' installed via auto-provisioning without toolchain repositories. This behavior has been deprecated. This will fail with an error in Gradle 10. Builds may fail when this toolchain is not available in other environments. Add toolchain repositories to this build. For more information, please refer to https://docs.gradle.org/current/userguide/toolchains.html#sub:download_repositories in the Gradle documentation.")
        succeeds("compileJava", "-Porg.gradle.java.installations.auto-detect=false", "-Porg.gradle.java.installations.auto-download=true")
    }

    @Requires(value = [IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable])
    def "toolchain download can handle different jvms with the same archive name"() {
        when:
        executer.requireOwnGradleUserHomeDir("needs to test toolchain download functionality").withToolchainDownloadEnabled()
        succeeds("compileJava")

        then: "suitable JDK gets auto-provisioned"
        assertJdkWasDownloaded()

        when:
        def differentJdk = AvailableJavaHomes.getDifferentVersion(EXPECTED_JVM.javaVersion)
        def jdkRepository2 = new JdkRepository(differentJdk.javaVersion)
        def uri2 = jdkRepository2.start()
        jdkRepository2.reset()

        and:
        settingsFile.text = settingsFile.text.replace(uri.toString(), uri2.toString())
        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${differentJdk.javaVersion.majorVersion})
                }
            }
        """

        and:
        executer.requireOwnGradleUserHomeDir("needs to test toolchain download functionality").withToolchainDownloadEnabled()
        succeeds("compileJava")

        then: "another suitable JDK gets auto-provisioned"
        assertJdkWasDownloaded(differentJdk)

        cleanup:
        jdkRepository2.stop()
    }

    @Requires(value = [IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable])
    def "toolchain download can handle corrupted archive"() {
        when:
        executer.requireOwnGradleUserHomeDir("needs to test toolchain download functionality").withToolchainDownloadEnabled()
        succeeds("compileJava")

        then: "suitable JDK gets auto-provisioned"
        assertJdkWasDownloaded()

        when:
        executer.gradleUserHomeDir.file("jdks")
            .listFiles({ file -> file.name.endsWith(".zip") } as FileFilter)
            .each { it.text = "corrupted data" }

        // delete unpacked JDKs
        executer.gradleUserHomeDir.file("jdks")
            .listFiles({ file -> file.isDirectory() } as FileFilter)
            .each { it.deleteDir() }

        jdkRepository.reset()

        // invalidating compilation
        file("src/main/java/Bar.java") << "public class Bar {}"

        and:
        executer
            .requireOwnGradleUserHomeDir("needs to test toolchain download functionality")
            .withToolchainDownloadEnabled()
        executer.withStackTraceChecksDisabled()
        succeeds("compileJava", "--info")

        then:
        output.matches("(?s).*Re-downloading toolchain from URI .* because unpacking the existing archive .* failed with an exception.*")
        result.assertTasksExecutedAndNotSkipped(":compileJava")
        assertJdkWasDownloaded()
    }

    private void assertJdkWasDownloaded(Jvm jvm = EXPECTED_JVM) {
        assert executer.gradleUserHomeDir.file("jdks").listFiles({ file ->
            file.name.contains("-${jvm.javaVersion.majorVersion}-")
        } as FileFilter)
    }

    private static File findMarkerFile(File directory) {
        File markerFile
        new SingleIncludePatternFileTree(directory, "**").visit(new FileVisitor() {
            @Override
            void visitDir(FileVisitDetails dirDetails) {
            }

            @Override
            void visitFile(FileVisitDetails fileDetails) {
                if (fileDetails.file.name == ".ready") {
                    markerFile = fileDetails.file
                }
            }
        })
        if (markerFile == null) {
            throw new RuntimeException("Marker file not found in " + directory.getAbsolutePath() + "")
        }
        return markerFile
    }

}
