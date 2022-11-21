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

class JavaToolchainDownloadSoakTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            plugins {
                id "java"
            }

            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(14)
                }
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        executer.requireOwnGradleUserHomeDir()
        executer
            .withToolchainDetectionEnabled()
            .withToolchainDownloadEnabled()
    }

    def "can download missing jdk automatically"() {
        when:
        result = executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
                .expectDocumentedDeprecationWarning("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository. " +
                        "This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 8.0. " +
                        "In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block. " +
                        "See https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning for more details.")
                .run()

        then:
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded("adoptopenjdk")
    }

    def "can download missing j9 jdk automatically"() {
        buildFile << """
            java {
                toolchain {
                    implementation = JvmImplementation.J9
                }
            }
        """

        when:
        result = executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
                .expectDocumentedDeprecationWarning("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository. " +
                        "This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 8.0. " +
                        "In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block. " +
                        "See https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning for more details.")
                .run()

        then:
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded("openj9")
    }

    def "clean destination folder when downloading toolchain"() {
        when: "build runs and doesn't have a local JDK to use for compilation"
        result = executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false")
                .expectDocumentedDeprecationWarning("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository. " +
                        "This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 8.0. " +
                        "In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block. " +
                        "See https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning for more details.")
                .run()

        then: "suitable JDK gets auto-provisioned"
        javaClassFile("Foo.class").assertExists()
        assertJdkWasDownloaded("adoptopenjdk")

        when: "the marker file of the auto-provisioned JDK is deleted, making the JDK not detectable"
        //delete marker file to make the previously downloaded installation undetectable
        def markerFile = findMarkerFile(executer.gradleUserHomeDir.file("jdks"))
        markerFile.delete()
        assert !markerFile.exists()

        and: "build runs again"
        executer
                .withTasks("compileJava", "-Porg.gradle.java.installations.auto-detect=false", "-Porg.gradle.java.installations.auto-download=true")
                .expectDocumentedDeprecationWarning("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository. " +
                        "This behavior has been deprecated. This behavior is scheduled to be removed in Gradle 8.0. " +
                        "In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block. " +
                        "See https://docs.gradle.org/current/userguide/toolchains.html#sec:provisioning for more details.")
                .run()

        then: "the JDK is auto-provisioned again and its files, even though they are already there don't trigger an error, they just get overwritten"
        markerFile.exists()
    }

    private void assertJdkWasDownloaded(String implementation) {
        assert executer.gradleUserHomeDir.file("jdks").listFiles({ file ->
            file.name.contains("-14-") && file.name.contains(implementation)
        } as FileFilter)
    }

    def cleanup() {
        executer.gradleUserHomeDir.file("jdks").deleteDir()
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
}
