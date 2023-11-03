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

import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.ProviderFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class RtxInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def rtxHomeDirectory = temporaryFolder.createDir("rtx")

    def "supplies no installations for absent property"() {
        given:
        def supplier = createSupplier(null)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }


    def "supplies no installations for empty property"() {
        given:
        def supplier = createSupplier("")

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for non-existing directory"() {
        assert rtxHomeDirectory.delete()

        given:
        def supplier = createSupplier(rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for empty directory"() {
        given:
        def supplier = createSupplier(rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for all empty directories"() {
        given:
        def supplier = createSupplier(rtxHomeDirectory.absolutePath, rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single candidate"() {
        given:
        rtxHomeDirectory.createDir("installs/java/11.0.6.hs-adpt")
        def supplier = createSupplier(rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([new File(rtxHomeDirectory, "installs/java/11.0.6.hs-adpt").absolutePath])
        directories*.source == ["rtx"]
    }

    def "supplies single installations for single candidate in user.home"() {
        given:
        rtxHomeDirectory.createDir(".local/share/rtx/installs/java/11.0.6.hs-adpt")
        def supplier = createSupplier(null, rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([new File(rtxHomeDirectory, ".local/share/rtx/installs/java/11.0.6.hs-adpt").absolutePath])
        directories*.source == ["rtx"]
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        rtxHomeDirectory.createDir("installs/java/11.0.6.hs-adpt")
        rtxHomeDirectory.createDir("installs/java/14")
        rtxHomeDirectory.createDir("installs/java/8.0.262.fx-librca")
        def supplier = createSupplier(rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(rtxHomeDirectory, "installs/java/11.0.6.hs-adpt").absolutePath,
            new File(rtxHomeDirectory, "installs/java/14").absolutePath,
            new File(rtxHomeDirectory, "installs/java/8.0.262.fx-librca").absolutePath
        ])
        directories*.source == ["rtx", "rtx", "rtx"]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "supplies installations with symlinked candidate"() {
        given:
        def otherLocation = temporaryFolder.createDir("other")
        def javaCandidates = rtxHomeDirectory.createDir("installs/java")
        javaCandidates.createDir("14-real")
        javaCandidates.file("other-symlinked").createLink(otherLocation.canonicalFile)
        def supplier = createSupplier(rtxHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(rtxHomeDirectory, "installs/java/14-real").absolutePath,
            new File(rtxHomeDirectory, "installs/java/other-symlinked").absolutePath
        ])
        directories*.source == ["rtx", "rtx"]
    }

    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    RtxInstallationSupplier createSupplier(String propertyValue) {
        new RtxInstallationSupplier(createProviderFactory(propertyValue))
    }

    RtxInstallationSupplier createSupplier(String rtxDataDir, String userHome) {
        new RtxInstallationSupplier(createProviderFactory(rtxDataDir, userHome))
    }

    ProviderFactory createProviderFactory(String rtxDataDir, String userHome = null) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.environmentVariable("RTX_DATA_DIR") >> Providers.ofNullable(rtxDataDir)
        providerFactory.environmentVariable("XDG_DATA_HOME") >> Providers.ofNullable(null)
        providerFactory.systemProperty("user.home") >> Providers.ofNullable(userHome)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable(null)
        providerFactory
    }
}
