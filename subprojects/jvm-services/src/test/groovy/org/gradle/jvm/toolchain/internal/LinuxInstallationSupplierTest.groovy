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
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

@CleanupTestDirectory
class LinuxInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());

    def candidates = temporaryFolder.createDir("linux")
    def otherCandidates = temporaryFolder.createDir("centos")

    def "supplies no installations for non-existing directory"() {
        assert candidates.delete()

        given:
        def supplier = createSupplier(candidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "detects installations in default roots"() {
        given:
        def supplier = new LinuxInstallationSupplier(createProviderFactory())

        when:
        def directories = supplier.get()

        then:
        directories != null
    }

    def "supplies no installations for empty directory"() {
        given:
        def supplier = createSupplier(candidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations non-linux operating system"() {
        given:
        candidates.createDir("11.0.6.hs-adpt")
        def supplier = new LinuxInstallationSupplier(createProviderFactory(), OperatingSystem.WINDOWS, candidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single candidate"() {
        given:
        candidates.createDir("11.0.6.hs-adpt")
        def supplier = createSupplier(candidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([new File(candidates, "11.0.6.hs-adpt").absolutePath])
        directories*.source == ["Common Linux Locations"]
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        candidates.createDir("11.0.6.hs-adpt")
        candidates.createDir("14")
        candidates.createDir("8.0.262.fx-librca")
        def supplier = createSupplier(candidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(candidates, "11.0.6.hs-adpt").absolutePath,
            new File(candidates, "14").absolutePath,
            new File(candidates, "8.0.262.fx-librca").absolutePath
        ])
        directories*.source == ["Common Linux Locations", "Common Linux Locations", "Common Linux Locations"]
    }

    def "supplies installations for multiple locations"() {
        given:
        candidates.createDir("11.0.6.hs-adpt")
        otherCandidates.createDir("8.0.262.fx-librca")
        def supplier = createSupplier(candidates.absolutePath, otherCandidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(otherCandidates, "8.0.262.fx-librca").absolutePath,
            new File(candidates, "11.0.6.hs-adpt").absolutePath
        ])
        directories*.source == ["Common Linux Locations", "Common Linux Locations"]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "supplies installations with symlinked candidate"() {
        given:
        def otherLocation = temporaryFolder.createDir("other")
        candidates.createDir("14-real")
        candidates.file("other-symlinked").createLink(otherLocation.canonicalFile)
        def supplier = createSupplier(candidates.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(candidates, "14-real").absolutePath,
            new File(candidates, "other-symlinked").absolutePath
        ])
        directories*.source == ["Common Linux Locations", "Common Linux Locations"]
    }

    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    InstallationSupplier createSupplier(String... roots) {
        new LinuxInstallationSupplier(createProviderFactory(), OperatingSystem.LINUX, roots)
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable(null)
        providerFactory
    }
}
