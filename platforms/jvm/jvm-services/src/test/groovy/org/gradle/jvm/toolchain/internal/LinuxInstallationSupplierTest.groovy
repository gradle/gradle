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


import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class LinuxInstallationSupplierTest extends Specification {
    @Rule
    private final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def candidates = temporaryFolder.createDir("linux")
    def otherCandidates = temporaryFolder.createDir("other")
    def emptyDir = temporaryFolder.createDir("empty")
    def nonExistent = temporaryFolder.file("non-existent")

    def setup() {
        candidates.createDir("11.0.6.hs-adpt")
        candidates.createDir("14")
        otherCandidates.createDir("8.0.262.fx-librca")
    }

    def "supplies no installations non-linux operating system"() {
        given:
        def supplier = new LinuxInstallationSupplier(OperatingSystem.WINDOWS, candidates)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "has default roots"() {
        given:
        def supplier = new LinuxInstallationSupplier()

        expect:
        supplier.roots.length > 1
    }

    def "supplies no installations for non-existing directory"() {
        given:
        def supplier = new LinuxInstallationSupplier(OperatingSystem.LINUX, nonExistent)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }


    def "supplies no installations for empty directory"() {
        given:
        def supplier = new LinuxInstallationSupplier(OperatingSystem.LINUX, emptyDir)

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        def supplier = new LinuxInstallationSupplier(OperatingSystem.LINUX, candidates)

        when:
        def directories = supplier.get()

        then:
        directories.size() == 2
        directories*.location.toSorted() == [
            candidates.file("11.0.6.hs-adpt"),
            candidates.file("14")
        ]
        directories*.source.unique() == ["Common Linux Locations"]
    }

    def "supplies installations for multiple locations"() {
        given:
        def supplier = new LinuxInstallationSupplier(OperatingSystem.LINUX, candidates, otherCandidates)

        when:
        def directories = supplier.get()

        then:
        directories.size() == 3
        directories*.location.toSorted() == [
            candidates.file("11.0.6.hs-adpt"),
            candidates.file("14"),
            otherCandidates.file("8.0.262.fx-librca")
        ]
        directories*.source.unique() == ["Common Linux Locations"]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "supplies installations with symlinked candidate"() {
        given:
        def otherLocation = temporaryFolder.createDir("other")
        candidates.createDir("14-real")
        candidates.file("symlinked").createLink(otherLocation.canonicalFile)

        def supplier = new LinuxInstallationSupplier(OperatingSystem.LINUX, candidates)

        when:
        def directories = supplier.get()

        then:
        directories.size() == 4
        directories*.location.toSorted() == [
            candidates.file("11.0.6.hs-adpt"),
            candidates.file("14"),
            candidates.file("14-real"),
            candidates.file("symlinked"),
        ]
        directories*.source.unique() == ["Common Linux Locations"]
    }
}
