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

import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.provider.Provider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class DefaultProjectLayoutTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    TestFile projectDir
    DefaultProjectLayout layout

    def setup() {
        projectDir = tmpDir.createDir("project")
        layout = new DefaultProjectLayout(projectDir, TestFiles.resolver(projectDir), Stub(TaskResolver), Stub(FileCollectionFactory))
    }

    def "can query the project directory"() {
        expect:
        layout.projectDirectory.getAsFile() == projectDir
    }

    def "can resolve directory relative to project directory"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def dir = layout.projectDirectory.dir("sub-dir")
        dir.getAsFile() == projectDir.file("sub-dir")

        def provider = layout.projectDirectory.dir(pathProvider)
        provider.present

        def dir1 = provider.get()
        dir1.getAsFile() == projectDir.file("a")

        def dir2 = provider.get()
        dir2.getAsFile() == projectDir.file("b")

        dir1.getAsFile() == projectDir.file("a")
        dir2.getAsFile() == projectDir.file("b")
    }

    def "can resolve regular file relative to project directory"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def file = layout.projectDirectory.file("child")
        file.getAsFile() == projectDir.file("child")

        def provider = layout.projectDirectory.file(pathProvider)
        provider.present

        def file1 = provider.get()
        file1.getAsFile() == projectDir.file("a")

        def file2 = provider.get()
        file2.getAsFile() == projectDir.file("b")

        file1.getAsFile() == projectDir.file("a")
        file2.getAsFile() == projectDir.file("b")
    }

    def "directory is not present when path provider is not present"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.present >> false
        _ * pathProvider.getOrNull() >> null

        expect:
        def provider = layout.projectDirectory.dir(pathProvider)
        !provider.present
        provider.getOrNull() == null
    }

    def "regular file is not present when path provider is not present"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.present >> false
        _ * pathProvider.getOrNull() >> null

        expect:
        def provider = layout.projectDirectory.file(pathProvider)
        !provider.present
        provider.getOrNull() == null
    }

    def "can view directory as a file tree"() {
        def dir1 = projectDir.createDir("dir1")
        def file1 = dir1.createFile("sub-dir/file1")
        def file2 = dir1.createFile("file2")
        def dir2 = projectDir.createDir("dir2")
        def file3 = dir2.createFile("other/file3")

        expect:
        def tree1 = layout.projectDirectory.dir("dir1").asFileTree
        tree1.files == [file1, file2] as Set

        def tree2 = layout.projectDirectory.dir("dir2").asFileTree
        tree2.files == [file3] as Set
    }

    def "can create directory property"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.get() >> { "../other-dir" }
        _ * pathProvider.present >> true
        def otherDir = tmpDir.file("other-dir")

        expect:
        def dirVar = layout.directoryProperty()
        def fileProvider = dirVar.asFile
        !dirVar.present
        dirVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null

        dirVar.set(otherDir)
        dirVar.present
        dirVar.get().getAsFile() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir

        dirVar.set(layout.projectDirectory.dir("../other-dir"))
        dirVar.present
        dirVar.get().getAsFile() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir

        dirVar.set(layout.projectDirectory.dir(pathProvider))
        dirVar.present
        dirVar.get().getAsFile() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir

        dirVar.set((File) null)
        !dirVar.present
        dirVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null
    }

    def "can create directory property with initial provided value"() {
        def initialProvider = layout.directoryProperty()
        initialProvider.set(layout.projectDirectory.dir("../other-dir"))
        def otherDir = tmpDir.file("other-dir")

        expect:
        def dirVar = layout.directoryProperty(initialProvider)
        def fileProvider = dirVar.asFile

        dirVar.present
        dirVar.get().getAsFile() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir
    }

    def "can create regular file property"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.get() >> { "../some-file" }
        _ * pathProvider.present >> true
        def otherFile = tmpDir.file("some-file")

        expect:
        def fileVar = layout.fileProperty()
        def fileProvider = fileVar.asFile
        !fileVar.present
        fileVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null

        fileVar.set(otherFile)
        fileVar.present
        fileVar.get().getAsFile() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile

        fileVar.set(layout.projectDirectory.file("../some-file"))
        fileVar.present
        fileVar.get().getAsFile() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile

        fileVar.set(layout.projectDirectory.file(pathProvider))
        fileVar.present
        fileVar.get().getAsFile() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile

        fileVar.set((File) null)
        !fileVar.present
        fileVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null
    }

    def "can create regular file property with initial provided value"() {
        def initialProvider = layout.fileProperty()
        initialProvider.set(layout.projectDirectory.file("../some-file"))
        def otherFile = tmpDir.file("some-file")

        expect:
        def fileVar = layout.fileProperty(initialProvider)
        def fileProvider = fileVar.asFile

        fileVar.present
        fileVar.get().getAsFile() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile
    }

    def "can set directory property using a relative File"() {
        def otherDir = projectDir.file("sub-dir")

        expect:
        def dirVar = layout.directoryProperty()
        def fileProvider = dirVar.asFile

        dirVar.set(new File("sub-dir"))
        dirVar.present
        dirVar.get().getAsFile() == otherDir
        fileProvider.present
        fileProvider.get() == otherDir
    }

    def "can set file property using a relative File"() {
        def otherFile = projectDir.file("some-file")

        expect:
        def fileVar = layout.fileProperty()
        def fileProvider = fileVar.asFile

        fileVar.set(new File("some-file"))
        fileVar.present
        fileVar.get().getAsFile() == otherFile
        fileProvider.present
        fileProvider.get() == otherFile
    }

    def "can set directory property untyped using a File"() {
        def otherDir = projectDir.file("sub-dir")

        expect:
        def dirVar = layout.directoryProperty()

        dirVar.setFromAnyValue(new File("sub-dir"))
        dirVar.present
        dirVar.get().getAsFile() == otherDir
    }

    def "can set file property untyped using a File"() {
        def otherFile = projectDir.file("some-file")

        expect:
        def fileVar = layout.fileProperty()

        fileVar.setFromAnyValue(new File("some-file"))
        fileVar.present
        fileVar.get().getAsFile() == otherFile
    }

    def "can resolve directory relative to calculated directory"() {
        def dirProvider = Stub(ProviderInternal)
        def pathProvider = Stub(ProviderInternal)
        def dir1 = projectDir.file("dir1")

        _ * dirProvider.type >> Directory
        _ * dirProvider.get() >>> [layout.projectDirectory.dir("d1"), layout.projectDirectory.dir("d2")]
        _ * dirProvider.present >> true
        _ * pathProvider.get() >>> ["c1", "c2"]
        _ * pathProvider.present >> true

        expect:
        def dirVar = layout.directoryProperty()
        def calculated1 = dirVar.dir("c1")

        !calculated1.present
        calculated1.getOrNull() == null

        dirVar.set(dir1)
        calculated1.present
        calculated1.get().getAsFile() == dir1.file("c1")

        dirVar.set(dirProvider)
        calculated1.present
        calculated1.get().getAsFile() == projectDir.file("d1/c1")
        calculated1.get().getAsFile() == projectDir.file("d2/c1")

        dirVar.set((File) null)
        !calculated1.present
        calculated1.getOrNull() == null

        def calculated2 = dirVar.dir(pathProvider)
        !calculated2.present
        calculated2.getOrNull() == null

        dirVar.set(dir1)
        calculated2.present
        calculated2.get().getAsFile() == dir1.file("c1")
        calculated2.get().getAsFile() == dir1.file("c2")
    }

    def "can resolve regular file relative to calculated directory"() {
        def dirProvider = Stub(ProviderInternal)
        def pathProvider = Stub(Provider)
        def dir1 = projectDir.file("dir1")

        _ * dirProvider.type >> Directory
        _ * dirProvider.get() >>> [layout.projectDirectory.dir("d1"), layout.projectDirectory.dir("d2")]
        _ * dirProvider.present >> true
        _ * pathProvider.get() >>> ["c1", "c2"]
        _ * pathProvider.present >> true

        expect:
        def dirVar = layout.directoryProperty()
        def calculated1 = dirVar.file("c1")

        !calculated1.present
        calculated1.getOrNull() == null

        dirVar.set(dir1)
        calculated1.present
        calculated1.get().getAsFile() == dir1.file("c1")

        dirVar.set(dirProvider)
        calculated1.present
        calculated1.get().getAsFile() == projectDir.file("d1/c1")
        calculated1.get().getAsFile() == projectDir.file("d2/c1")

        dirVar.set((File) null)
        !calculated1.present
        calculated1.getOrNull() == null

        def calculated2 = dirVar.file(pathProvider)
        !calculated2.present
        calculated2.getOrNull() == null

        dirVar.set(dir1)
        calculated2.present
        calculated2.get().getAsFile() == dir1.file("c1")
        calculated2.get().getAsFile() == dir1.file("c2")
    }

    def "can view directory property as a file tree"() {
        def dir1 = projectDir.createDir("dir1")
        def file1 = dir1.createFile("sub-dir/file1")
        def file2 = dir1.createFile("file2")
        def dir2 = projectDir.createDir("dir2")
        def file3 = dir2.createFile("other/file3")
        def dir3 = projectDir.file("missing")

        expect:
        def dirVar = layout.directoryProperty()
        def tree = dirVar.asFileTree

        dirVar.set(dir1)
        tree.files == [file1, file2] as Set

        dirVar.set(dir2)
        tree.files == [file3] as Set

        dirVar.set(dir3)
        tree.files == [] as Set
    }

    def "cannot query the views of a directory property when the property has no value"() {
        def dirVar = layout.directoryProperty()
        def tree = dirVar.asFileTree
        def fileProvider = dirVar.asFile
        def dir = dirVar.dir("dir")
        def file = dirVar.file("dir")

        when:
        dirVar.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this provider.'

        when:
        fileProvider.get()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'No value has been specified for this provider.'

        when:
        dir.get()

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'No value has been specified for this provider.'

        when:
        file.get()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'No value has been specified for this provider.'

        when:
        tree.files

        then:
        def e5 = thrown(IllegalStateException)
        e5.message == 'No value has been specified for this provider.'
    }

    def "cannot set the value of a directory property using incompatible type"() {
        def var = layout.directoryProperty()

        when:
        var.set(123)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot set the value of a property of type org.gradle.api.file.Directory using an instance of type java.lang.Integer."

        and:
        !var.present
    }

    def "cannot set the value of a regular file property using incompatible type"() {
        def var = layout.fileProperty()

        when:
        var.set(123)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot set the value of a property of type org.gradle.api.file.RegularFile using an instance of type java.lang.Integer."

        and:
        !var.present
    }

    def "producer task for a directory is not known by default"() {
        def var = layout.directoryProperty()
        def context = Mock(TaskDependencyResolveContext)

        when:
        def visited = var.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }

    def "can specify the producer task for a directory"() {
        def var = layout.directoryProperty()
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)

        when:
        var.attachProducer(task)
        def visited = var.maybeVisitBuildDependencies(context)

        then:
        visited
        1 * context.add(task)
        0 * context._
    }

    def "can discard the producer task for a directory"() {
        def var = layout.directoryProperty()
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)

        when:
        var.attachProducer(task)
        def visited = var.locationOnly.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }

    def "producer task for a regular file is not known by default"() {
        def var = layout.fileProperty()
        def context = Mock(TaskDependencyResolveContext)

        when:
        def visited = var.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }

    def "can specify the producer task for a regular file"() {
        def var = layout.fileProperty()
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)

        when:
        var.attachProducer(task)
        def visited = var.maybeVisitBuildDependencies(context)

        then:
        visited
        1 * context.add(task)
        0 * context._
    }

    def "can discard the producer task for a regular file"() {
        def var = layout.fileProperty()
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)

        when:
        var.attachProducer(task)
        def visited = var.locationOnly.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }

    def "can query and mutate the build directory using resolvable type"() {
        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.present
        fileProvider.present
        fileProvider.get() == projectDir.file("build")

        def dir1 = buildDirectory.get()
        dir1.getAsFile() == projectDir.file("build")

        layout.setBuildDirectory("other")
        buildDirectory.present
        fileProvider.present
        fileProvider.get() == projectDir.file("other")

        def dir2 = buildDirectory.get()
        dir2.getAsFile() == projectDir.file("other")

        layout.setBuildDirectory("../target")
        buildDirectory.present
        fileProvider.present
        fileProvider.get() == tmpDir.file("target")

        def dir3 = buildDirectory.get()
        dir3.getAsFile() == tmpDir.file("target")

        dir1.getAsFile() == projectDir.file("build")
        dir2.getAsFile() == projectDir.file("other")
        dir3.getAsFile() == tmpDir.file("target")
    }

    def "can set the build directory location using an absolute File"() {
        def dir = tmpDir.createDir("dir")

        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.set(dir)
        buildDirectory.present
        fileProvider.present
        buildDirectory.get().getAsFile() == dir
        fileProvider.get() == dir
    }

    def "can set the build directory location using a relative File"() {
        expect:
        def buildDirectory = layout.buildDirectory
        def fileProvider = buildDirectory.asFile

        buildDirectory.set(new File("other"))
        buildDirectory.present
        fileProvider.present
        buildDirectory.get().getAsFile() == projectDir.file("other")
        fileProvider.get() == projectDir.file("other")
    }

    def "can resolve directory relative to build directory"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def dir = layout.buildDirectory.dir("sub-dir")
        dir.present
        dir.get().getAsFile() == projectDir.file("build/sub-dir")

        def provider = layout.buildDirectory.dir(pathProvider)
        provider.present

        def dir1 = provider.get()
        dir1.getAsFile() == projectDir.file("build/a")

        def dir2 = provider.get()
        dir2.getAsFile() == projectDir.file("build/b")

        dir1.getAsFile() == projectDir.file("build/a")
        dir2.getAsFile() == projectDir.file("build/b")
    }

    def "can resolve regular file relative to build directory"() {
        def pathProvider = Stub(Provider)
        _ * pathProvider.get() >>> ["a", "b"]
        _ * pathProvider.present >> true

        expect:
        def file = layout.buildDirectory.file("child")
        file.present
        file.get().getAsFile() == projectDir.file("build/child")

        def provider = layout.buildDirectory.file(pathProvider)
        provider.present

        def file1 = provider.get()
        file1.getAsFile() == projectDir.file("build/a")

        def file2 = provider.get()
        file2.getAsFile() == projectDir.file("build/b")

        file1.getAsFile() == projectDir.file("build/a")
        file2.getAsFile() == projectDir.file("build/b")
    }

    def "directories are equal when their paths are equal"() {
        expect:
        def dir = layout.projectDirectory.dir("child")
        strictlyEquals(dir, layout.projectDirectory.dir("child"))

        dir != layout.projectDirectory.dir("other")
        dir != layout.projectDirectory.dir("child/child2")
        dir != layout.projectDirectory.file("child")
    }

    def "regular files are equal when their paths are equal"() {
        expect:
        def file = layout.projectDirectory.file("child")
        strictlyEquals(file, layout.projectDirectory.file("child"))

        file != layout.projectDirectory.file("other")
        file != layout.projectDirectory.file("child/child2")
        file != layout.projectDirectory.dir("child")
    }

    def "can wrap File provider"() {
        def fileProvider = Stub(ProviderInternal)
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")

        _ * fileProvider.present >> true
        _ * fileProvider.get() >>> [file1, file2]

        expect:
        def provider = layout.file(fileProvider)
        provider.present

        provider.get().getAsFile() == file1
        provider.get().getAsFile() == file2
    }

    def "resolves relative files given by File provider"() {
        def fileProvider = Stub(ProviderInternal)
        def file1 = projectDir.file("file1")
        def file2 = projectDir.file("file2")

        _ * fileProvider.present >> true
        _ * fileProvider.get() >>> [new File("file1"), new File("file2")]

        expect:
        def provider = layout.file(fileProvider)
        provider.present

        provider.get().getAsFile() == file1
        provider.get().getAsFile() == file2
    }
}
