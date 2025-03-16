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

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

@CleanupTestDirectory
class SdkmanInstallationSupplierTest extends Specification {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def toolchainConfiguration = Mock(ToolchainConfiguration)
    TestFile candidates

    @Subject
    def supplier = new SdkmanInstallationSupplier(toolchainConfiguration)

    def setup() {
        candidates = temporaryFolder.createDir("candidates")
        toolchainConfiguration.getSdkmanCandidatesDirectory() >> candidates

    }

    def "supplies no installations for empty directory"() {
        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies no installations for non-existing directory"() {
        given:
        assert candidates.deleteDir()

        when:
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single candidate"() {
        given:
        def expectedLocation = candidates.createDir("java/11.0.6.hs-adpt")

        when:
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == expectedLocation
        directories[0].source == "SDKMAN!"
    }

    def "supplies multiple installations for multiple paths"() {
        given:
        def expectedLocation1 = candidates.createDir("java/11.0.6.hs-adpt")
        def expectedLocation2 = candidates.createDir("java/14")
        def expectedLocation3 = candidates.createDir("java/8.0.262.fx-librca")

        when:
        def directories = supplier.get()

        then:
        directories.size() == 3
        directories*.location.containsAll(expectedLocation1, expectedLocation2, expectedLocation3)
        directories*.source.unique() == ["SDKMAN!"]
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "supplies installations with symlinked candidate"() {
        given:
        def real = candidates.createDir("java/11.0.6.hs-adpt")
        def symlink = candidates.file("java/symlink").createLink(real)

        when:
        def directories = supplier.get()

        then:
        directories.size() == 2
        directories*.location.containsAll(real, symlink)
        directories*.source.unique() == ["SDKMAN!"]

        def otherLocation = temporaryFolder.createDir("other")
        def javaCandidates = candidates.createDir("java")
        javaCandidates.createDir("14-real")
        javaCandidates.file("other-symlinked").createLink(otherLocation.canonicalFile)
    }
}
