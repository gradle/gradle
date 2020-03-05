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
package org.gradle.integtests.fixtures.jvm

import org.gradle.api.JavaVersion
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.UsesNativeServices
import org.gradle.util.VersionNumber
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class UbuntuJvmLocatorTest extends Specification {

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def libDir = tmpDir.file("lib")
    def fileCanonicalizer = Stub(FileCanonicalizer) {
        canonicalize(_) >> { File f -> f.canonicalFile }
    }
    def locator = new UbuntuJvmLocator(new File(libDir.absolutePath), fileCanonicalizer)

    def "finds no JVMs when lib directory does not exist"() {
        expect:
        locator.findJvms().empty
    }

    def "locates JREs installed in lib directory"() {
        given:
        jre("java-1.6.0-openjdk-amd64")
        jre("java-1.6.0-openjdk-i386")
        jre("java-1.7.0-openjdk-amd64")
        jre("java-1.7.0-openjdk-i386")
        libDir.createDir("not-a-jre")
        libDir.createDir("java-1.5.0-openjdk-amd64")

        expect:
        def jvms = locator.findJvms()
        jvms.size() == 4
        jvms.sort { it.javaHome.name }

        jvms[0].javaVersion == JavaVersion.VERSION_1_6
        jvms[0].version == VersionNumber.parse("1.6.0")
        jvms[0].arch == JvmInstallation.Arch.x86_64
        !jvms[0].jdk
        jvms[0].javaHome == libDir.file("java-1.6.0-openjdk-amd64")

        jvms[3].javaVersion == JavaVersion.VERSION_1_7
        jvms[3].version == VersionNumber.parse("1.7.0")
        jvms[3].arch == JvmInstallation.Arch.i386
        !jvms[3].jdk
        jvms[3].javaHome == libDir.file("java-1.7.0-openjdk-i386")
    }

    def "locates JDKs installed in lib directory"() {
        given:
        jre("java-1.6.0-openjdk-amd64")
        jdk("java-1.7.0-openjdk-amd64")
        libDir.createDir("not-a-jre")
        libDir.createDir("java-1.5.0-openjdk-amd64")

        expect:
        def jvms = locator.findJvms()
        jvms.size() == 2
        jvms.sort { it.javaHome.name }

        jvms[0].javaVersion == JavaVersion.VERSION_1_6
        !jvms[0].jdk

        jvms[1].javaVersion == JavaVersion.VERSION_1_7
        jvms[1].jdk
    }

    @Requires(TestPrecondition.SYMLINKS)
    def "locates JDK in canonicalized directory"() {
        given:
        jdk("real-install/java-1.7-openjdk-amd64")
        def link = libDir.file("java-1.7.0-openjdk-amd64")
        link.createLink("real-install/java-1.7-openjdk-amd64")

        expect:
        def jvms = locator.findJvms()
        jvms.size() == 1

        jvms[0].javaVersion == JavaVersion.VERSION_1_7
        jvms[0].jdk
        jvms[0].javaHome == libDir.file("real-install/java-1.7-openjdk-amd64")

        cleanup:
        link.delete()
    }

    def jre(String name) {
        libDir.createFile("${name}/bin/java")
        libDir.createFile("${name}/jre/bin/java")
    }

    TestFile jdk(String name) {
        jre(name)
        libDir.createFile("${name}/bin/javac")
    }
}
