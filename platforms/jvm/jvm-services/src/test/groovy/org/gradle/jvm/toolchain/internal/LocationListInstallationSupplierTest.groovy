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


import org.gradle.api.internal.file.IdentityFileResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class LocationListInstallationSupplierTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final buildOptions = Mock(ToolchainConfiguration)

    @Subject
    def supplier = new LocationListInstallationSupplier(buildOptions, new IdentityFileResolver())

    def "supplies no installations for empty property"() {
        when:
        buildOptions.installationsFromPaths >> []
        def directories = supplier.get()

        then:
        directories.isEmpty()
    }

    def "supplies single installations for single path"() {
        when:
        def expectedFile = tmpDir.createDir("foo/bar")
        buildOptions.installationsFromPaths >> [expectedFile.absolutePath]
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == expectedFile
        directories[0].source == "Gradle property 'org.gradle.java.installations.paths'"
    }

    def "supplies multiple installations for multiple paths"() {
        when:
        def expectedFile1 = tmpDir.createDir("foo/bar")
        def expectedFile2 = tmpDir.createDir("foo/123")
        buildOptions.installationsFromPaths >> [expectedFile1.absolutePath, expectedFile2.absolutePath]
        def directories = supplier.get()

        then:
        directories.size() == 2
        directories*.location.containsAll(expectedFile1, expectedFile2)
        directories*.source.unique() == [ "Gradle property 'org.gradle.java.installations.paths'" ]
    }
}
