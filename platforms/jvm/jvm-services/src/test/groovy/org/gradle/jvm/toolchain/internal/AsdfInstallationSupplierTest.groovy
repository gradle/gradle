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

class AsdfInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def asdfHomeDirectory = temporaryFolder.createDir("asdf")

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
        assert asdfHomeDirectory.delete()

        given:
        def supplier = createSupplier(asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for empty directory"() {
        given:
        def supplier = createSupplier(asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for all empty directories"() {
        given:
        def supplier = createSupplier(asdfHomeDirectory.absolutePath, asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single candidate"() {
        given:
        asdfHomeDirectory.createDir("installs/java/11.0.6.hs-adpt")
        def supplier = createSupplier(asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([new File(asdfHomeDirectory, "installs/java/11.0.6.hs-adpt").absolutePath])
        directories*.source == ["asdf-vm"]
    }

    def "supplies single installations for single candidate in user.home"() {
        given:
        asdfHomeDirectory.createDir(".asdf/installs/java/11.0.6.hs-adpt")
        def supplier = createSupplier(null, asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([new File(asdfHomeDirectory, ".asdf/installs/java/11.0.6.hs-adpt").absolutePath])
        directories*.source == ["asdf-vm"]
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        asdfHomeDirectory.createDir("installs/java/11.0.6.hs-adpt")
        asdfHomeDirectory.createDir("installs/java/14")
        asdfHomeDirectory.createDir("installs/java/8.0.262.fx-librca")
        def supplier = createSupplier(asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(asdfHomeDirectory, "installs/java/11.0.6.hs-adpt").absolutePath,
            new File(asdfHomeDirectory, "installs/java/14").absolutePath,
            new File(asdfHomeDirectory, "installs/java/8.0.262.fx-librca").absolutePath
        ])
        directories*.source == ["asdf-vm", "asdf-vm", "asdf-vm"]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "supplies installations with symlinked candidate"() {
        given:
        def otherLocation = temporaryFolder.createDir("other")
        def javaCandidates = asdfHomeDirectory.createDir("installs/java")
        javaCandidates.createDir("14-real")
        javaCandidates.file("other-symlinked").createLink(otherLocation.canonicalFile)
        def supplier = createSupplier(asdfHomeDirectory.absolutePath)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(asdfHomeDirectory, "installs/java/14-real").absolutePath,
            new File(asdfHomeDirectory, "installs/java/other-symlinked").absolutePath
        ])
        directories*.source == ["asdf-vm", "asdf-vm"]
    }

    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    AsdfInstallationSupplier createSupplier(String propertyValue) {
        new AsdfInstallationSupplier(createProviderFactory(propertyValue))
    }

    AsdfInstallationSupplier createSupplier(String asdfDataDir, String userHome) {
        new AsdfInstallationSupplier(createProviderFactory(asdfDataDir, userHome))
    }

    ProviderFactory createProviderFactory(String asdfDataDir, String userHome = null) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.environmentVariable("ASDF_DATA_DIR") >> Providers.ofNullable(asdfDataDir)
        providerFactory.systemProperty("user.home") >> Providers.ofNullable(userHome)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.ofNullable(null)
        providerFactory
    }
}
