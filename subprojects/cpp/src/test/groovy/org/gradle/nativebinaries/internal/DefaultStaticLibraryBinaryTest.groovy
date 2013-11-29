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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.language.HeaderExportingSourceSet
import org.gradle.language.base.internal.DefaultBinaryNamingScheme
import org.gradle.nativebinaries.BuildType
import org.gradle.nativebinaries.Library
import org.gradle.nativebinaries.Platform
import org.gradle.nativebinaries.internal.resolve.NativeDependencyResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultStaticLibraryBinaryTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir
    def namingScheme = new DefaultBinaryNamingScheme("main")
    def library = Stub(Library)
    def toolChain = Stub(ToolChainInternal)
    def platform = Stub(Platform)
    def buildType = Stub(BuildType)
    final resolver = Stub(NativeDependencyResolver)

    def "has useful string representation"() {  
        expect:
        staticLibrary.toString() == "static library 'main:staticLibrary'"
    }

    def getStaticLibrary() {
        new DefaultStaticLibraryBinary(library, new DefaultFlavor("flavorOne"), toolChain, platform, buildType, namingScheme, resolver)
    }

    def "can convert binary to a native dependency"() {
        final binary = staticLibrary
        given:
        def headers = Stub(SourceDirectorySet)
        library.headers >> headers
        def lifecycleTask = Stub(Task)
        binary.lifecycleTask = lifecycleTask
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
        def nativeDependency = binary.resolve()
        nativeDependency.includeRoots.files == [headerDir] as Set

        and:
        nativeDependency.linkFiles.files == [binary.outputFile] as Set
        nativeDependency.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        nativeDependency.linkFiles.toString() == "static library 'main:staticLibrary'"

        and:
        nativeDependency.runtimeFiles.files.isEmpty()
        nativeDependency.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        nativeDependency.runtimeFiles.toString() == "static library 'main:staticLibrary'"
    }

    def "includes additional link files in native dependency"() {
        final binary = staticLibrary
        given:
        def linkFile1 = Mock(File)
        def linkFile2 = Mock(File)
        def additionalLinkFiles = Stub(FileCollection) {
            getFiles() >> [linkFile1, linkFile2]
        }
        binary.additionalLinkFiles(additionalLinkFiles)

        expect:
        def nativeDependency = binary.resolve()
        nativeDependency.linkFiles.files == [binary.outputFile, linkFile1, linkFile2] as Set
    }
}
