/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.internal.provider.ProviderInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultFilePropertyFactoryTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    TestFile projectDir
    DefaultFilePropertyFactory factory

    def setup() {
        projectDir = tmpDir.createDir("project")
        factory = new DefaultFilePropertyFactory(TestFiles.resolver(projectDir), TestFiles.fileCollectionFactory())
    }

    def "can create directory instance from absolute file"() {
        def location = tmpDir.createDir("dir")

        expect:
        def dir = factory.dir(location)
        dir.asFile == location
    }

    def "can create directory instance from relative file"() {
        def location = projectDir.createDir("dir")

        expect:
        def dir = factory.dir(new File("dir"))
        dir.asFile == location
    }

    def "can create file instance from absolute file"() {
        def location = tmpDir.createFile("file")

        expect:
        def file = factory.file(location)
        file.asFile == location
    }

    def "can create file instance from relative file"() {
        def location = projectDir.createFile("file")

        expect:
        def file = factory.file(new File("file"))
        file.asFile == location
    }

    def "can create directory property"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.get() >> { "../other-dir" }
        _ * pathProvider.present >> true
        def otherDir = tmpDir.file("other-dir")

        expect:
        def dirVar = factory.newDirectoryProperty()
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

        dirVar.set((File) null)
        !dirVar.present
        dirVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null
    }

    def "can create regular file property"() {
        def pathProvider = Stub(ProviderInternal)
        _ * pathProvider.get() >> { "../some-file" }
        _ * pathProvider.present >> true
        def otherFile = tmpDir.file("some-file")

        expect:
        def fileVar = factory.newFileProperty()
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

        fileVar.set((File) null)
        !fileVar.present
        fileVar.getOrNull() == null
        !fileProvider.present
        fileProvider.getOrNull() == null
    }

    def "can set directory property using a relative File"() {
        def otherDir = projectDir.file("sub-dir")

        expect:
        def dirVar = factory.newDirectoryProperty()
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
        def fileVar = factory.newFileProperty()
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
        def dirVar = factory.newDirectoryProperty()

        dirVar.setFromAnyValue(new File("sub-dir"))
        dirVar.present
        dirVar.get().getAsFile() == otherDir
    }

    def "can set file property untyped using a File"() {
        def otherFile = projectDir.file("some-file")

        expect:
        def fileVar = factory.newFileProperty()

        fileVar.setFromAnyValue(new File("some-file"))
        fileVar.present
        fileVar.get().getAsFile() == otherFile
    }

    def "can view directory property as a file tree"() {
        def dir1 = projectDir.createDir("dir1")
        def file1 = dir1.createFile("sub-dir/file1")
        def file2 = dir1.createFile("file2")
        def dir2 = projectDir.createDir("dir2")
        def file3 = dir2.createFile("other/file3")
        def dir3 = projectDir.file("missing")

        expect:
        def dirVar = factory.newDirectoryProperty()
        def tree = dirVar.asFileTree

        dirVar.set(dir1)
        tree.files == [file1, file2] as Set

        dirVar.set(dir2)
        tree.files == [file3] as Set

        dirVar.set(dir3)
        tree.files == [] as Set
    }

    def "cannot query the views of a directory property when the property has no value"() {
        def dirVar = factory.newDirectoryProperty()
        def tree = dirVar.asFileTree
        def fileProvider = dirVar.asFile
        def dir = dirVar.dir("dir")
        def file = dirVar.file("dir")

        when:
        dirVar.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'No value has been specified for this property.'

        when:
        fileProvider.get()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'No value has been specified for this property.'

        when:
        dir.get()

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'No value has been specified for this property.'

        when:
        file.get()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'No value has been specified for this property.'

        when:
        tree.files

        then:
        def e5 = thrown(IllegalStateException)
        e5.message == 'No value has been specified for this property.'
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "cannot set the value of a directory property using incompatible type"() {
        def var = factory.newDirectoryProperty()

        when:
        var.set(123)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot set the value of a property of type org.gradle.api.file.Directory using an instance of type java.lang.Integer."

        and:
        !var.present
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "cannot set the value of a regular file property using incompatible type"() {
        def var = factory.newFileProperty()

        when:
        var.set(123)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot set the value of a property of type org.gradle.api.file.RegularFile using an instance of type java.lang.Integer."

        and:
        !var.present
    }

    def "producer task for a directory is not known by default"() {
        def var = factory.newDirectoryProperty()
        def context = Mock(TaskDependencyResolveContext)

        when:
        def visited = var.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }

    def "can specify the producer task for a directory"() {
        def var = factory.newDirectoryProperty()
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
        def var = factory.newDirectoryProperty()
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
        def var = factory.newFileProperty()
        def context = Mock(TaskDependencyResolveContext)

        when:
        def visited = var.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }

    def "can specify the producer task for a regular file"() {
        def var = factory.newFileProperty()
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
        def var = factory.newFileProperty()
        def task = Mock(Task)
        def context = Mock(TaskDependencyResolveContext)

        when:
        var.attachProducer(task)
        def visited = var.locationOnly.maybeVisitBuildDependencies(context)

        then:
        !visited
        0 * context._
    }
}
