/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal.prebuilt

import org.gradle.api.internal.file.TestFiles
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.Flavor
import org.gradle.nativeplatform.PrebuiltLibrary
import org.gradle.nativeplatform.platform.NativePlatform
import spock.lang.Specification

class DefaultPrebuiltSharedLibraryBinaryTest extends Specification {
    def prebuiltLibrary = Stub(PrebuiltLibrary) {
        getName() >> "lib"
    }
    def binary = new DefaultPrebuiltSharedLibraryBinary("name", prebuiltLibrary, Stub(BuildType), Stub(NativePlatform), Stub(Flavor), TestFiles.fileCollectionFactory())

    def "has useful string representation"() {
        expect:
        binary.toString() == "prebuilt shared library 'lib:name'"
        binary.displayName == "prebuilt shared library 'lib:name'"
        binary.linkFiles.toString() == "link files for prebuilt shared library 'lib:name'"
        binary.runtimeFiles.toString() == "runtime files for prebuilt shared library 'lib:name'"
    }

    def "uses library file when link file not set"() {
        given:
        def sharedLibraryFile = Mock(File)
        def sharedLibraryLinkFile = Mock(File)

        when:
        binary.sharedLibraryFile = sharedLibraryFile

        then:
        binary.sharedLibraryFile == sharedLibraryFile
        binary.sharedLibraryLinkFile == sharedLibraryFile

        when:
        binary.sharedLibraryLinkFile = sharedLibraryLinkFile

        then:
        binary.sharedLibraryFile == sharedLibraryFile
        binary.sharedLibraryLinkFile == sharedLibraryLinkFile
    }

    def "uses specified link file and library file"() {
        given:
        def sharedLibraryFile = createFile()
        def sharedLibraryLinkFile = createFile()

        when:
        binary.sharedLibraryFile = sharedLibraryFile
        binary.sharedLibraryLinkFile = sharedLibraryLinkFile

        then:
        binary.linkFiles.files == [sharedLibraryLinkFile] as Set

        binary.runtimeFiles.files == [sharedLibraryFile] as Set
    }

    def createFile() {
        def file = Stub(File) {
            exists() >> true
            isFile() >> true
        }
    }
}
