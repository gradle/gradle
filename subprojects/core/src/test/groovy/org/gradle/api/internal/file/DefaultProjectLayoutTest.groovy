/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.file

import org.gradle.api.provider.Provider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultProjectLayoutTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    TestFile projectDir
    DefaultProjectLayout layout

    def setup() {
        projectDir = tmpDir.createDir("project")
        layout = new DefaultProjectLayout(projectDir, TestFiles.resolver(projectDir))
    }

    def "can query the project directory"() {
        def projectDir = tmpDir.createDir("project")
        def layout = new DefaultProjectLayout(projectDir, TestFiles.resolver(projectDir))

        expect:
        layout.projectDirectory.get() == projectDir
        layout.projectDirectory.present
    }

    def "can resolve directory relative to project directory"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def dir = layout.projectDirectory.dir("sub-dir")
        dir.present
        dir.get() == projectDir.file("sub-dir")

        def provider = layout.projectDirectory.dir(pathProvider)
        provider.present

        def dir1 = provider.get()
        dir1.get() == projectDir.file("a")

        def dir2 = provider.get()
        dir2.get() == projectDir.file("b")

        dir1.get() == projectDir.file("a")
        dir2.get() == projectDir.file("b")
    }

    def "can resolve regular file relative to project directory"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def file = layout.projectDirectory.file("child")
        file.present
        file.get() == projectDir.file("child")

        def provider = layout.projectDirectory.file(pathProvider)
        provider.present

        def file1 = provider.get()
        file1.get() == projectDir.file("a")

        def file2 = provider.get()
        file2.get() == projectDir.file("b")

        file1.get() == projectDir.file("a")
        file2.get() == projectDir.file("b")
    }

    def "directory is not present when path provider is not present"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.present >> false

        expect:
        def provider = layout.projectDirectory.dir(pathProvider)
        !provider.present
        provider.getOrNull() == null
    }

    def "regular file is not present when path provider is not present"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.present >> false

        expect:
        def provider = layout.projectDirectory.file(pathProvider)
        !provider.present
        provider.getOrNull() == null
    }

    def "can create directory var"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >> { "../other-dir" }
        _ * pathProvider.present >> true
        def otherDir = tmpDir.file("other-dir")

        expect:
        def dirVar = layout.newDirectoryVar()
        def fileProvider = dirVar.asFile
        !dirVar.present
        dirVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null

        dirVar.set(otherDir)
        dirVar.present
        dirVar.get().get() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir

        dirVar.set(layout.projectDirectory.dir("../other-dir"))
        dirVar.present
        dirVar.get().get() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir

        dirVar.set(layout.projectDirectory.dir(pathProvider))
        dirVar.present
        dirVar.get().get() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir

        dirVar.set((File)null)
        !dirVar.present
        dirVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null
    }

    def "can create regular file var"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >> { "../some-file" }
        _ * pathProvider.present >> true
        def otherFile = tmpDir.file("some-file")

        expect:
        def fileVar = layout.newFileVar()
        def fileProvider = fileVar.asFile
        !fileVar.present
        fileVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null

        fileVar.set(otherFile)
        fileVar.present
        fileVar.get().get() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile

        fileVar.set(layout.projectDirectory.file("../some-file"))
        fileVar.present
        fileVar.get().get() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile

        fileVar.set(layout.projectDirectory.file(pathProvider))
        fileVar.present
        fileVar.get().get() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile

        fileVar.set((File) null)
        !fileVar.present
        fileVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null
    }

    def "can set directory var using a relative File"() {
        def otherDir = projectDir.file("sub-dir")

        expect:
        def dirVar = layout.newDirectoryVar()
        def fileProvider = dirVar.asFile

        dirVar.set(new File("sub-dir"))
        dirVar.present
        dirVar.get().get() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir
    }

    def "can set file var using a relative File"() {
        def otherFile = projectDir.file("some-file")

        expect:
        def fileVar = layout.newFileVar()
        def fileProvider = fileVar.asFile

        fileVar.set(new File("some-file"))
        fileVar.present
        fileVar.get().get() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile
    }

    def "can resolve directory relative to calculated directory"() {
        def dirProvider = Stub(Provider)
        def pathProvider = Stub(Provider)
        def dir1 = projectDir.file("dir1")

        _ * dirProvider.get() >>> [layout.projectDirectory.dir("d1"), layout.projectDirectory.dir("d2")]
        _ * dirProvider.present >> true
        _ * pathProvider.get() >>> ["c1", "c2"]
        _ * pathProvider.present >> true

        expect:
        def dirVar = layout.newDirectoryVar()
        def calculated1 = dirVar.dir("c1")

        !calculated1.present
        calculated1.getOrNull() == null

        dirVar.set(dir1)
        calculated1.present
        calculated1.get().get() == dir1.file("c1")

        dirVar.set(dirProvider)
        calculated1.present
        calculated1.get().get() == projectDir.file("d1/c1")
        calculated1.get().get() == projectDir.file("d2/c1")

        dirVar.set((File) null)
        !calculated1.present
        calculated1.getOrNull() == null

        def calculated2 = dirVar.dir(pathProvider)
        !calculated2.present
        calculated2.getOrNull() == null

        dirVar.set(dir1)
        calculated2.present
        calculated2.get().get() == dir1.file("c1")
        calculated2.get().get() == dir1.file("c2")
    }

    def "can resolve regular file relative to calculated directory"() {
        def dirProvider = Stub(Provider)
        def pathProvider = Stub(Provider)
        def dir1 = projectDir.file("dir1")

        _ * dirProvider.get() >>> [layout.projectDirectory.dir("d1"), layout.projectDirectory.dir("d2")]
        _ * dirProvider.present >> true
        _ * pathProvider.get() >>> ["c1", "c2"]
        _ * pathProvider.present >> true

        expect:
        def dirVar = layout.newDirectoryVar()
        def calculated1 = dirVar.file("c1")

        !calculated1.present
        calculated1.getOrNull() == null

        dirVar.set(dir1)
        calculated1.present
        calculated1.get().get() == dir1.file("c1")

        dirVar.set(dirProvider)
        calculated1.present
        calculated1.get().get() == projectDir.file("d1/c1")
        calculated1.get().get() == projectDir.file("d2/c1")

        dirVar.set((File) null)
        !calculated1.present
        calculated1.getOrNull() == null

        def calculated2 = dirVar.file(pathProvider)
        !calculated2.present
        calculated2.getOrNull() == null

        dirVar.set(dir1)
        calculated2.present
        calculated2.get().get() == dir1.file("c1")
        calculated2.get().get() == dir1.file("c2")
    }

    def "can query and mutate the build directory using resolveable type"() {
        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.present
        fileProvider.present
        fileProvider.get() == projectDir.file("build")

        def dir1 = buildDirectory.get()
        dir1.get() == projectDir.file("build")

        layout.setBuildDirectory("other")
        buildDirectory.present
        fileProvider.present
        fileProvider.get() == projectDir.file("other")

        def dir2 = buildDirectory.get()
        dir2.get() == projectDir.file("other")

        layout.setBuildDirectory("../target")
        buildDirectory.present
        fileProvider.present
        fileProvider.get() == tmpDir.file("target")

        def dir3 = buildDirectory.get()
        dir3.get() == tmpDir.file("target")

        dir1.get() == projectDir.file("build")
        dir2.get() == projectDir.file("other")
        dir3.get() == tmpDir.file("target")
    }

    def "can set the build directory location using an absolute File"() {
        def dir = tmpDir.createDir("dir")

        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.set(dir)
        buildDirectory.present
        fileProvider.present
        buildDirectory.get().get() == dir
        fileProvider.get() == dir
    }

    def "can set the build directory location using a relative File"() {
        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.set(new File("other"))
        buildDirectory.present
        fileProvider.present
        buildDirectory.get().get() == projectDir.file("other")
        fileProvider.get() == projectDir.file("other")
    }

    def "can set the build directory location using a directory instance"() {

    }

    def "can set the build directory location using a directory provider"() {

    }

    def "can resolve directory relative to build directory"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def dir = layout.buildDirectory.dir("sub-dir")
        dir.present
        dir.get().get() == projectDir.file("build/sub-dir")

        def provider = layout.buildDirectory.dir(pathProvider)
        provider.present

        def dir1 = provider.get()
        dir1.get() == projectDir.file("build/a")

        def dir2 = provider.get()
        dir2.get() == projectDir.file("build/b")

        dir1.get() == projectDir.file("build/a")
        dir2.get() == projectDir.file("build/b")
    }

    def "can resolve regular file relative to build directory"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def file = layout.buildDirectory.file("child")
        file.present
        file.get().get() == projectDir.file("build/child")

        def provider = layout.buildDirectory.file(pathProvider)
        provider.present

        def file1 = provider.get()
        file1.get() == projectDir.file("build/a")

        def file2 = provider.get()
        file2.get() == projectDir.file("build/b")

        file1.get() == projectDir.file("build/a")
        file2.get() == projectDir.file("build/b")
    }

    def "can wrap File provider"() {
        def fileProvider = Stub(Provider)
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")

        _ * fileProvider.present >> true
        _ * fileProvider.get() >>> [file1, file2]

        expect:
        def provider = layout.file(fileProvider)
        provider.present

        provider.get().get() == file1
        provider.get().get() == file2
    }

    def "resolves relative files given by File provider"() {
        def fileProvider = Stub(Provider)
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")

        _ * fileProvider.present >> true
        _ * fileProvider.get() >>> [new File("file1"), new File("file2")]

        expect:
        def provider = layout.file(fileProvider)
        provider.present

        provider.get().get() == file1
        provider.get().get() == file2
    }
}
