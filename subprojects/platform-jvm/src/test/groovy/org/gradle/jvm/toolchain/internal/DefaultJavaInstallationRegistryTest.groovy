/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.api.file.Directory
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.Providers
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultJavaInstallationRegistryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def probe = Mock(JavaInstallationProbe)
    def registry = new DefaultJavaInstallationRegistry(probe, TestUtil.providerFactory(), TestFiles.fileCollectionFactory(), TestFiles.fileFactory())

    def "can query information for current JVM"() {
        def probeResult = Stub(JavaInstallationProbe.ProbeResult)
        def javaHome = new File("java-home").absoluteFile

        when:
        def provider = registry.installationForCurrentVirtualMachine

        then:
        1 * probe.current() >> probeResult
        _ * probeResult.javaHome >> javaHome

        and:
        provider.present
        provider.get().installationDirectory.asFile == javaHome

        when:
        def result = provider.get()

        then:
        0 * probe._

        and:
        result.installationDirectory.asFile == javaHome
    }

    def "can lazily query information for installation in directory"() {
        def dir = Stub(Directory)
        def probeResult = Stub(JavaInstallationProbe.ProbeResult)
        def javaHome = tmpDir.createDir("java-home").absoluteFile

        when:
        def provider = registry.installationForDirectory(dir)

        then:
        0 * probe._

        when:
        def result = provider.get()

        then:
        1 * probe.checkJdk(javaHome) >> probeResult
        _ * dir.asFile >> javaHome
        _ * probeResult.javaHome >> javaHome

        and:
        result.installationDirectory.asFile == javaHome

        when:
        def result2 = provider.get()

        then:
        0 * probe._

        and:
        result2.is(result)
    }

    def "has no information when no directory defined"() {
        when:
        def provider = registry.installationForDirectory(Providers.notDefined())

        then:
        0 * probe._

        when:
        def present = provider.present
        def result = provider.getOrNull()

        then:
        0 * probe._

        and:
        !present
        result == null
    }

    def "fails when directory does not exist"() {
        def missing = tmpDir.file("missing")
        def dir = Stub(Directory)
        _ * dir.toString() >> "<dir>"
        _ * dir.asFile >> missing

        when:
        def provider = registry.installationForDirectory(dir)

        then:
        0 * probe._

        when:
        provider.getOrNull()

        then:
        def e = thrown(DefaultJavaInstallationRegistry.JavaInstallationDiscoveryException)
        e.message == "Could not determine the details of Java installation in directory <dir>."
        e.cause instanceof FileNotFoundException
        e.cause.message == "Directory ${missing} does not exist."

        and:
        0 * probe._
    }
}
