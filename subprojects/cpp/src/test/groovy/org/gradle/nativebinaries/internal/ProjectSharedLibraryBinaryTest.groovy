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

package org.gradle.nativebinaries.internal
import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.runtime.base.internal.DefaultBinaryNamingScheme
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.NativeLibrary
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import org.gradle.nativebinaries.platform.Platform
import org.gradle.nativebinaries.toolchain.internal.ToolChainInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class ProjectSharedLibraryBinaryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    def namingScheme = new DefaultBinaryNamingScheme("main", "sharedLibrary", [])
    final toolChain = Stub(ToolChainInternal)
    final platform = Stub(Platform)
    final buildType = Stub(BuildType)
    final library = Stub(NativeLibrary)
    final resolver = Stub(NativeDependencyResolver)
    def sharedLibraryFile = Mock(File)
    def sharedLibraryLinkFile = Mock(File)

    def "has useful string representation"() {
        expect:
        sharedLibrary.toString() == "shared library 'main:sharedLibrary'"
    }

    def "can set output files"() {
        given:
        def binary = sharedLibrary

        when:
        binary.sharedLibraryFile = sharedLibraryFile
        binary.sharedLibraryLinkFile = sharedLibraryLinkFile

        then:
        binary.sharedLibraryFile == sharedLibraryFile
        binary.sharedLibraryLinkFile == sharedLibraryLinkFile
    }

    def "can convert binary to a native dependency"() {
        given:
        def binary = sharedLibrary
        binary.sharedLibraryFile = sharedLibraryFile
        binary.sharedLibraryLinkFile = sharedLibraryLinkFile
        def lifecycleTask = Stub(Task)
        binary.setLifecycleTask(lifecycleTask)
        binary.builtBy(Stub(Task))

        and: "has at least one header exporting source set"
        final headerDir = tmpDir.createDir("headerDir")
        def headerDirSet = Stub(SourceDirectorySet) {
            getSrcDirs() >> [headerDir]
        }
        def sourceDirSet = Stub(SourceDirectorySet) {
            getFiles() >> [tmpDir.createFile("input.src")]
        }
        def sourceSet = Stub(HeaderExportingSourceSet) {
            getSource() >> sourceDirSet
            getExportedHeaders() >> headerDirSet
        }
        binary.source sourceSet

        expect:
        binary.sharedLibraryFile == sharedLibraryFile
        binary.sharedLibraryLinkFile == sharedLibraryLinkFile

        binary.headerDirs.files == [headerDir] as Set

        and:
        binary.linkFiles.files == [binary.sharedLibraryLinkFile] as Set
        binary.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        binary.linkFiles.toString() == "shared library 'main:sharedLibrary'"

        and:
        binary.runtimeFiles.files == [binary.sharedLibraryFile] as Set
        binary.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        binary.runtimeFiles.toString() == "shared library 'main:sharedLibrary'"
    }

    def "has empty link files when has resources and no symbols are exported from library"() {
        when:
        def binary = sharedLibrary
        def sourceDirSet = Stub(SourceDirectorySet) {
            getFiles() >> [tmpDir.createFile("input.rc")]
        }
        def resourceSet = Stub(WindowsResourceSet) {
            getSource() >> sourceDirSet
        }
        binary.source resourceSet

        def binaryFile = tmpDir.createFile("binary.run")
        def linkFile = tmpDir.createFile("binary.link")
        toolChain.getSharedLibraryLinkFileName(binaryFile.path) >> linkFile.path

        then:
        binary.linkFiles.files == [] as Set
    }

    private ProjectSharedLibraryBinary getSharedLibrary() {
        new ProjectSharedLibraryBinary(library, new DefaultFlavor("flavorOne"), toolChain, platform, buildType, namingScheme, resolver)
    }
}
