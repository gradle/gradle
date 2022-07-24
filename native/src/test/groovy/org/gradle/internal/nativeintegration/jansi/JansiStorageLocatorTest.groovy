/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.jansi

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class JansiStorageLocatorTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def locator = new JansiStorageLocator()
    def factory = Mock(JansiLibraryFactory)

    def setup() {
        locator.factory = factory
    }

    def "provides storage information if jansi library can be resolved"() {
        given:
        def platform = 'linux32'
        def nativeLibraryName = 'nativelib.so'
        def jansiLibrary = new JansiLibrary(platform, nativeLibraryName)

        when:
        def jansiStorage = locator.locate(tmpDir.testDirectory)

        then:
        1 * factory.create() >> jansiLibrary
        jansiStorage.jansiLibrary == jansiLibrary
        jansiStorage.targetLibFile == new File(locator.makeVersionSpecificDir(tmpDir.testDirectory), "$platform/$nativeLibraryName")
    }

    def "returns null if jansi library cannot be created for unsupported OS"() {
        when:
        def jansiStorage = locator.locate(tmpDir.testDirectory)

        then:
        1 * factory.create() >> null
        !jansiStorage
    }
}
