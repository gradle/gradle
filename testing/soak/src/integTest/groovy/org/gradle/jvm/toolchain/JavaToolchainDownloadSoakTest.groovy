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


import org.gradle.api.JavaVersion
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.applyToolchainResolverPlugin
import static org.gradle.jvm.toolchain.JavaToolchainDownloadUtil.singleUrlResolverCode

@Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
class JavaToolchainDownloadSoakTest extends AbstractIntegrationSpec {

    public static final JavaVersion JAVA_VERSION = AvailableJavaHomes.differentVersion.javaVersion


    public static final String TOOLCHAIN_WITH_VERSION = """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(${JAVA_VERSION.majorVersion})
                }
            }
        """

    static JdkRepository jdkRepository

    static URI uri

    def setupSpec() {
        jdkRepository = new JdkRepository(JAVA_VERSION)
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

            $TOOLCHAIN_WITH_VERSION
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
            file.name.contains("-${JAVA_VERSION.majorVersion}-") && (implementation == null || file.name.contains(implementation))
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
