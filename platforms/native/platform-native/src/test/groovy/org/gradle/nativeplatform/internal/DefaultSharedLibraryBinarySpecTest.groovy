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

package org.gradle.nativeplatform.internal

import org.gradle.api.Task
import org.gradle.api.file.SourceDirectorySet
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.language.nativeplatform.NativeResourceSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.SharedLibraryBinarySpec
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.LinkSharedLibrary
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultSharedLibraryBinarySpecTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def namingScheme = DefaultBinaryNamingScheme.component("main").withBinaryType("sharedLibrary")
    final toolChain = Stub(NativeToolChainInternal)
    final platform = Stub(NativePlatform)
    final buildType = Stub(BuildType)
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
        binary.setBuildTask(lifecycleTask)
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
            getSources() >> sourceDirSet
            getExportedHeaders() >> headerDirSet
        }
        binary.inputs.add sourceSet

        expect:
        binary.sharedLibraryFile == sharedLibraryFile
        binary.sharedLibraryLinkFile == sharedLibraryLinkFile

        binary.headerDirs.files == [headerDir] as Set
        binary.headerDirs.toString() == "Headers for shared library 'main:sharedLibrary'"

        and:
        binary.linkFiles.files == [binary.sharedLibraryLinkFile] as Set
        binary.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        binary.linkFiles.toString() == "Link files for shared library 'main:sharedLibrary'"

        and:
        binary.runtimeFiles.files == [binary.sharedLibraryFile] as Set
        binary.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        binary.runtimeFiles.toString() == "Runtime files for shared library 'main:sharedLibrary'"
    }

    def "has empty link files when has resources and no symbols are exported from library"() {
        when:
        def binary = sharedLibrary
        def sourceDirSet = Stub(SourceDirectorySet) {
            getFiles() >> [tmpDir.createFile("input.rc")]
        }
        def resourceSet = Stub(NativeResourceSet) {
            getSources() >> sourceDirSet
        }
        binary.inputs.add resourceSet

        def binaryFile = tmpDir.createFile("binary.run")
        def linkFile = tmpDir.createFile("binary.link")
        toolChain.getSharedLibraryLinkFileName(binaryFile.path) >> linkFile.path

        then:
        binary.linkFiles.files == [] as Set
    }

    def "returns null for link and builder when none defined"() {
        given:
        def binary = sharedLibrary

        expect:
        binary.tasks.build == null
        binary.tasks.link == null
    }

    def "returns link task when defined"() {
        given:
        def binary = sharedLibrary

        when:
        final linkTask = TestUtil.create(tmpDir).task(LinkSharedLibrary)
        binary.tasks.add(linkTask)

        then:
        binary.tasks.link == linkTask
    }

    private def getSharedLibrary() {
        def library = BaseComponentFixtures.createNode(NativeLibrarySpec, DefaultNativeLibrarySpec, new DefaultComponentSpecIdentifier("path", "libName"));
        TestNativeBinariesFactory.create(SharedLibraryBinarySpec, DefaultSharedLibraryBinarySpec, "test", library, namingScheme, resolver,
                                         platform, buildType, new DefaultFlavor("flavorOne"))
    }
}
