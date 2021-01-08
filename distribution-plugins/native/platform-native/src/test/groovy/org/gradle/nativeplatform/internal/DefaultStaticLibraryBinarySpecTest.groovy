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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.language.nativeplatform.HeaderExportingSourceSet
import org.gradle.nativeplatform.BuildType
import org.gradle.nativeplatform.NativeLibrarySpec
import org.gradle.nativeplatform.StaticLibraryBinarySpec
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolver
import org.gradle.nativeplatform.platform.NativePlatform
import org.gradle.nativeplatform.tasks.CreateStaticLibrary
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.platform.base.component.BaseComponentFixtures
import org.gradle.platform.base.internal.DefaultBinaryNamingScheme
import org.gradle.platform.base.internal.DefaultBinaryTasksCollection
import org.gradle.platform.base.internal.DefaultComponentSpecIdentifier
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultStaticLibraryBinarySpecTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    final library = BaseComponentFixtures.createNode(NativeLibrarySpec, DefaultNativeLibrarySpec, new DefaultComponentSpecIdentifier("path", "libName"))
    def namingScheme = DefaultBinaryNamingScheme.component("main").withBinaryType("staticLibrary")
    def toolChain = Stub(NativeToolChainInternal)
    def platform = Stub(NativePlatform)
    def buildType = Stub(BuildType)
    final resolver = Stub(NativeDependencyResolver)
    final outputFile = Mock(File)
    def tasks = new DefaultStaticLibraryBinarySpec.DefaultTasksCollection(new DefaultBinaryTasksCollection(null, null, CollectionCallbackActionDecorator.NOOP))

    def "has useful string representation"() {
        expect:
        staticLibrary.toString() == "static library 'main:staticLibrary'"
    }

    def getStaticLibrary() {
        TestNativeBinariesFactory.create(StaticLibraryBinarySpec, DefaultStaticLibraryBinarySpec, "test", library, namingScheme, resolver, platform,
            buildType, new DefaultFlavor("flavorOne"))
    }

    def "can set output file"() {
        given:
        final binary = staticLibrary
        def outputFile = Mock(File)

        when:
        binary.staticLibraryFile = outputFile

        then:
        binary.staticLibraryFile == outputFile
    }

    def "can convert binary to a native dependency"() {
        final binary = staticLibrary
        given:
        def lifecycleTask = Stub(Task)
        binary.buildTask = lifecycleTask
        binary.builtBy(Stub(Task))

        and:
        binary.staticLibraryFile = outputFile

        and:
        final headerDir = tmpDir.createDir("headerDir")
        addSources(binary, headerDir)

        expect:
        binary.headerDirs.files == [headerDir] as Set
        binary.headerDirs.toString() == "Headers for static library 'main:staticLibrary'"
        binary.staticLibraryFile == outputFile

        and:
        binary.linkFiles.files == [binary.staticLibraryFile] as Set
        binary.linkFiles.buildDependencies.getDependencies(Stub(Task)) == [lifecycleTask] as Set
        binary.linkFiles.toString() == "Link files for static library 'main:staticLibrary'"

        and:
        binary.runtimeFiles.files.isEmpty()
        binary.runtimeFiles.buildDependencies.getDependencies(Stub(Task)) == [] as Set
        binary.runtimeFiles.toString() == "Runtime files for static library 'main:staticLibrary'"
    }

    def "includes additional link files in native dependency"() {
        final binary = staticLibrary
        given:
        binary.staticLibraryFile = outputFile
        def linkFile1 = Mock(File)
        def linkFile2 = Mock(File)
        def additionalLinkFiles = Stub(FileCollection) {
            getFiles() >> [linkFile1, linkFile2]
        }
        binary.additionalLinkFiles(additionalLinkFiles)

        and:
        addSources(binary, tmpDir.createDir("headerDir"))

        expect:
        binary.staticLibraryFile == outputFile
        binary.linkFiles.files == [binary.staticLibraryFile, linkFile1, linkFile2] as Set
    }

    def "returns null for createStaticLib and builder when none defined"() {
        expect:
        tasks.createStaticLib == null
    }

    def "returns create task when defined"() {
        when:
        final createTask = TestUtil.create(tmpDir).task(CreateStaticLibrary)
        tasks.add(createTask)

        then:
        tasks.createStaticLib == createTask
    }

    private TestFile addSources(StaticLibraryBinarySpec binary, def headerDir) {
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
        headerDir
    }
}
