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
import org.gradle.language.base.internal.DefaultBinaryNamingScheme
import org.gradle.language.rc.WindowsResourceSet
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.Library
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultSharedLibraryBinaryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    def namingScheme = new DefaultBinaryNamingScheme("main")
    final toolChain = Stub(ToolChainInternal)
    final platform = Stub(Platform)
    final buildType = Stub(BuildType)
    final library = Stub(Library)
    final resolver = Stub(NativeDependencyResolver)

    def "has useful string representation"() {
        expect:
        sharedLibrary.toString() == "shared library 'main:sharedLibrary'"
    }

    def "can convert binary to a native dependency"() {
        given:
        def binary = sharedLibrary
        def binaryFile = tmpDir.createFile("binary.run")
        def linkFile = tmpDir.createFile("binary.link")
        def lifecycleTask = Stub(Task)
        binary.setLifecycleTask(lifecycleTask)
        binary.builtBy(Stub(Task))
        binary.outputFile = binaryFile

        and:
        toolChain.getSharedLibraryLinkFileName(binaryFile.path) >> linkFile.path

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
        def nativeDependency = binary.resolve()
        nativeDependency.includeRoots.files == [headerDir] as Set

        and:
        nativeDependency.linkFiles.files == [linkFile] as Set
        nativeDependency.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        nativeDependency.linkFiles.toString() == "shared library 'main:sharedLibrary'"

        and:
        nativeDependency.runtimeFiles.files == [binaryFile] as Set
        nativeDependency.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        nativeDependency.runtimeFiles.toString() == "shared library 'main:sharedLibrary'"
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
        def nativeDependency = binary.resolve()
        nativeDependency.linkFiles.files == [] as Set
    }

    private DefaultSharedLibraryBinary getSharedLibrary() {
        new DefaultSharedLibraryBinary(library, new DefaultFlavor("flavorOne"), toolChain, platform, buildType, namingScheme, resolver)
    }
}
