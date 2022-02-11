/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.internal.SystemProperties
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.file.TestFiles.systemSpecificAbsolutePath

class IntellijIdeaInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def userHome = temporaryFolder.getTestDirectory().getCanonicalPath()
    def linuxRoot = temporaryFolder.createDir(".jdks")
    def macRoot = temporaryFolder.createDir("Library/Java/JavaVirtualMachines")

    def "supplies no installations if linux-specific directory is missing on linux"(boolean useProperty) {
        given:
        linuxRoot.delete()
        def supplier = useProperty ? createSupplierWithProperty(linuxRoot) : createSupplierWithUserHome(userHome, OperatingSystem.LINUX)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies no installations if mac-specific directory is missing on mac"(boolean useProperty) {
        given:
        macRoot.delete()
        def supplier = useProperty ? createSupplierWithProperty(macRoot) : createSupplierWithUserHome(userHome, OperatingSystem.MAC_OS)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()

        where:
        useProperty << [true, false]
    }

    def "supplies all directories from linux-specific directory if present on linux"(boolean useProperty) {
        given:
        linuxRoot.createDir("java-linux1")
        linuxRoot.createDir("java-linux2")
        macRoot.createDir("java-mac1")
        macRoot.createDir("java-mac2")
        def supplier = useProperty ? createSupplierWithProperty(linuxRoot) : createSupplierWithUserHome(userHome, OperatingSystem.LINUX)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(userHome, ".jdks/java-linux1").absolutePath,
            new File(userHome, ".jdks/java-linux2").absolutePath
        ])
        directories*.source == ["IntelliJ IDEA", "IntelliJ IDEA"]

        where:
        useProperty << [true, false]
    }

    def "supplies all directories from mac-specific directory if present on mac"(boolean useProperty) {
        given:
        linuxRoot.createDir("java-linux1")
        linuxRoot.createDir("java-linux2")
        macRoot.createDir("java-mac1")
        macRoot.createDir("java-mac2")
        def supplier = useProperty ? createSupplierWithProperty(macRoot) : createSupplierWithUserHome(userHome, OperatingSystem.MAC_OS)

        when:
        def directories = supplier.get()

        then:
        directoriesAsStablePaths(directories) == stablePaths([
            new File(userHome, "Library/Java/JavaVirtualMachines/java-mac1").absolutePath,
            new File(userHome, "Library/Java/JavaVirtualMachines/java-mac2").absolutePath
        ])
        directories*.source == ["IntelliJ IDEA", "IntelliJ IDEA"]

        where:
        useProperty << [true, false]
    }

    def directoriesAsStablePaths(Set<InstallationLocation> actualDirectories) {
        actualDirectories*.location.absolutePath.sort()
    }

    def stablePaths(List<String> expectedPaths) {
        expectedPaths.replaceAll({ String s -> systemSpecificAbsolutePath(s) })
        expectedPaths
    }

    IntellijIdeaInstallationSupplier createSupplierWithProperty(File rootDirectory) {
        new IntellijIdeaInstallationSupplier(createProviderFactory(rootDirectory.getCanonicalPath()))
    }

    IntellijIdeaInstallationSupplier createSupplierWithUserHome(String userHome, OperatingSystem os) {
        SystemProperties.instance.withSystemProperty("user.home", userHome) {
            new IntellijIdeaInstallationSupplier(createProviderFactory(null), os)
        }
    }

    ProviderFactory createProviderFactory(String propertyValue) {
        def providerFactory = Mock(ProviderFactory)
        providerFactory.gradleProperty("org.gradle.java.installations.auto-detect") >> Providers.notDefined()
        providerFactory.gradleProperty("org.gradle.java.installations.idea-jdks-directory") >> Providers.ofNullable(propertyValue)
        providerFactory
    }
}
