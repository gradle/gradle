/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.collections


import org.gradle.api.Task
import org.gradle.api.Transformer
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionSpec
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.specs.Spec
import org.spockframework.lang.Wildcard

import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Supplier

class DefaultConfigurableFileCollectionSpec extends FileCollectionSpec {

    def fileResolver = Mock(FileResolver)
    def taskResolver = Mock(TaskResolver)
    def host = Mock(PropertyHost)
    def patternSetFactory = TestFiles.patternSetFactory
    def taskDependencyFactory = DefaultTaskDependencyFactory.forProject(taskResolver, (tasks) -> { })
    def collection = new DefaultConfigurableFileCollection("<display>", fileResolver, taskDependencyFactory, patternSetFactory, host)

    @Override
    AbstractFileCollection containing(File... files) {
        def resolver = Stub(FileResolver)
        _ * resolver.resolve(_) >> { File f -> f }
            return new DefaultConfigurableFileCollection("<display>", resolver, taskDependencyFactory, patternSetFactory, host).from(files)
    }

    def "resolves specified files using file resolver"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        def collection = new DefaultConfigurableFileCollection("<display>", fileResolver, taskDependencyFactory, patternSetFactory, host).from("a", "b")
        def from = collection.from
        def files = collection.files

        then:
        1 * fileResolver.resolve("a") >> file1
        1 * fileResolver.resolve("b") >> file2
        from as List == ["a", "b"]
        files as List == [file1, file2]
    }

    def "can add paths to the collection"() {
        when:
        collection.from("src1", "src2")
        then:
        collection.from as List == ["src1", "src2"]
    }

    def "can set the paths of the collection"() {
        given:
        collection.from("ignore-me")

        when:
        collection.setFrom("src1", "src2")
        then:
        collection.from as List == ["src1", "src2"]

        when:
        collection.from = ["a", "b"]
        then:
        collection.from as List == [["a", "b"]]
    }

    def "can mutate the from collection"() {
        collection.from("src1", "src2")
        def from = collection.from

        when:
        from.clear()

        then:
        from.empty
        collection.from.empty

        when:
        def add1 = from.add('a')
        def add2 = from.add('b')
        def add3 = from.add('a')

        then:
        add1
        add2
        !add3

        and:
        from as List == ['a', 'b']
        collection.from as List == ['a', 'b']

        when:
        def remove1 = from.remove('unknown')
        def remove2 = from.remove('a')

        then:
        !remove1
        remove2

        and:
        from as List == ['b']
        collection.from as List == ['b']
    }

    def "resolves specified paths using file resolver"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        collection.from(["src1", "src2"])
        def files = collection.files

        then:
        1 * fileResolver.resolve("src1") >> file1
        1 * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "can use a closure to specify the contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        def paths = ["a"]
        collection.from({ paths })
        def files = collection.files

        then:
        1 * fileResolver.resolve("a") >> file1
        files as List == [file1]

        when:
        paths.add("b")
        files = collection.files

        then:
        1 * fileResolver.resolve("a") >> file1
        1 * fileResolver.resolve("b") >> file2
        files as List == [file1, file2]
    }

    def "can use a closure to specify a single file"() {
        given:
        def file = new File("1")

        when:
        collection.from({ 'a' as Character })
        def files = collection.files

        then:
        1 * fileResolver.resolve('a' as Character) >> file
        files as List == [file]
    }

    def "closure can return null"() {
        when:
        collection.from({ null })

        then:
        collection.files.empty
    }

    def "can use a collection to specify the contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def paths = ["src1"]

        when:
        collection.from(paths)
        def files = collection.files

        then:
        1 * fileResolver.resolve("src1") >> file1
        files as List == [file1]

        when:
        paths.add("src2")
        files = collection.files

        then:
        1 * fileResolver.resolve("src1") >> file1
        1 * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "can use an array to specify the contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        collection.from(["src1", "src2"] as String[])
        def files = collection.files

        then:
        1 * fileResolver.resolve("src1") >> file1
        1 * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "can use nested objects to specify the contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        collection.from({ [{ ['src1', { ['src2'] as String[] }] }] })
        def files = collection.files

        then:
        1 * fileResolver.resolve("src1") >> file1
        1 * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "can use a file collection with changing contents to specify the contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = Mock(MinimalFileSet)
        def srcCollection = TestFiles.fileCollectionFactory().create(src)

        when:
        collection.from(srcCollection)
        def files = collection.files

        then:
        1 * src.getFiles() >> ([file1] as Set)
        files as List == [file1]

        when:
        files = collection.files

        then:
        1 * src.getFiles() >> ([file1, file2] as LinkedHashSet)
        files == [file1, file2] as LinkedHashSet
    }

    def "can set another collection as convention to the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = Mock(MinimalFileSet)
        def srcCollection = TestFiles.fileCollectionFactory().create(src)

        when:
        collection.convention(srcCollection)
        def files = collection.files

        then:
        1 * src.getFiles() >> ([file1] as Set)
        files as List == [file1]

        when:
        files = collection.files

        then:
        1 * src.getFiles() >> ([file1, file2] as LinkedHashSet)
        files == [file1, file2] as LinkedHashSet
    }

    def "can use a callable to specify the contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def callable = Mock(Callable)

        when:
        collection.from(callable)
        def files = collection.files

        then:
        1 * callable.call() >> ["src1", "src2"]
        _ * fileResolver.resolve("src1") >> file1
        _ * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "can use a callable to specify the convention contents of the collection"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def callable = Mock(Callable)

        when:
        collection.convention(callable)
        def files = collection.files

        then:
        1 * callable.call() >> ["src1", "src2"]
        _ * fileResolver.resolve("src1") >> file1
        _ * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "callable can return null"() {
        given:
        def callable = Mock(Callable)

        when:
        collection.from(callable)
        def files = collection.files

        then:
        1 * callable.call() >> null
        0 * fileResolver._
        files.empty
    }

    def "can append contents to convention-based collection using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file2)

        when:
        collection.convention("src1")
        collection.convention(collection + src)
        def files = collection.files

        then:
        1 * fileResolver.resolve("src1") >> file1
        files as List == [file1, file2]
    }

    def "can append contents to empty collection using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file1, file2)

        when:
        collection.from = collection + src
        def files = collection.files

        then:
        files as List == [file1, file2]
    }

    def "can append contents to collection using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file2)

        when:
        collection.from = "src1"
        collection.from = collection + src
        def files = collection.files

        then:
        _ * fileResolver.resolve("src1") >> file1
        files as List == [file1, file2]
    }

    def "can append contents to collection defined via convention using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file2)

        when:
        collection.convention("src1")
        collection.convention(collection + src)
        def files = collection.files

        then:
        _ * fileResolver.resolve("src1") >> file1
        files as List == [file1, file2]
        !collection.explicit
    }

    def "can prepend contents to empty collection using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file1, file2)

        when:
        collection.from = src + collection
        def files = collection.files

        then:
        files as List == [file1, file2]
    }

    def "can prepend contents to collection using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file1)

        when:
        collection.from = "src2"
        collection.from = src + collection
        def files = collection.files

        then:
        _ * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def "can prepend contents to collection defined via convention using plus operator"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = containing(file1)

        when:
        collection.convention("src2")
        collection.convention(src + collection)
        def files = collection.files

        then:
        _ * fileResolver.resolve("src2") >> file2
        files as List == [file1, file2]
        !collection.explicit
    }

    def "can alternate multiple updates to collection convention and explicit values"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def file3 = new File("3")
        def file4 = new File("4")
        def file5 = new File("5")
        def src1 = containing(file1)
        def src2 = containing(file2)
        def src3 = containing(file3)
        def src4 = containing(file4)
        def src5 = containing(file5)

        when:
        collection.convention(src1)
        collection.from = src2
        collection.convention(src3 + collection)
        collection.from = collection + src4

        then:
        collection.files as List == [file2, file4]
        collection.explicit

        when:
        collection.unset()
        collection.convention(collection + src5)

        then:
        collection.files as List == [file3, file2, file5]
        !collection.explicit
    }

    def "elements provider tracks changes to content"() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def callable = Mock(Callable)

        collection.from(callable)
        def elements = collection.elements

        when:
        def f1 = elements.get()

        then:
        f1*.asFile == [file1, file2]

        and:
        1 * callable.call() >> ["src1", "src2"]
        _ * fileResolver.resolve("src1") >> file1
        _ * fileResolver.resolve("src2") >> file2
        0 * _

        when:
        def f2 = elements.get()

        then:
        f2*.asFile == [file2]

        and:
        1 * callable.call() >> ["2"]
        _ * fileResolver.resolve("2") >> file2
        0 * _
    }

    def "can get and set task dependencies"() {
        given:
        def task = Mock(Task)

        expect:
        collection.builtBy.empty

        when:
        collection.builtBy("a")
        collection.builtBy("b")
        collection.from("f")

        then:
        collection.builtBy == ["a", "b"] as Set<Object>

        when:
        collection.setBuiltBy(["c"])

        then:
        collection.builtBy == ["c"] as Set<Object>

        when:
        def dependencies = collection.buildDependencies.getDependencies(null)

        then:
        dependencies.toList() == [task]
        1 * taskResolver.resolveTask("c") >> task
        0 * _
    }

    def "task dependencies contains union of dependencies of nested file collections plus own dependencies"() {
        given:
        def fileCollectionMock = Mock(FileCollectionInternal)
        def taskA = Mock(Task)
        def taskB = Mock(Task)

        when:
        collection.from(fileCollectionMock)
        collection.from("f")
        collection.builtBy("b")
        def dependencies = collection.buildDependencies.getDependencies(null)

        then:
        dependencies.toList() == [taskA, taskB]
        1 * fileCollectionMock.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(taskA) }
        1 * taskResolver.resolveTask("b") >> taskB
        0 * _
    }

    def "has specified dependencies when empty"() {
        given:
        def task = Stub(Task)
        collection.builtBy("task")

        when:
        def dependencies = collection.buildDependencies.getDependencies(null)
        def fileTreeDependencies = collection.getAsFileTree().buildDependencies.getDependencies(null)
        def filteredFileTreeDependencies = collection.getAsFileTree().matching({}).buildDependencies.getDependencies(null)

        then:
        dependencies.toList() == [task]
        fileTreeDependencies.toList() == [task]
        filteredFileTreeDependencies.toList() == [task]
        3 * taskResolver.resolveTask("task") >> task
        0 * _
    }

    def "does not resolve paths when visiting dependencies"() {
        given:
        collection.from('ignore')

        when:
        collection.buildDependencies.getDependencies(null)

        then:
        0 * _
    }

    def "can visit structure when collection contains paths"() {
        def visitor = Mock(FileCollectionStructureVisitor)
        def one = testDir.file('one')
        def two = testDir.file('two')

        given:
        collection.from("a", "b")

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, collection) >> true
        1 * fileResolver.resolve('a') >> one
        1 * visitor.startVisit(FileCollectionInternal.OTHER, { it as List == [one] }) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, { it as List == [one] })
        1 * fileResolver.resolve('b') >> two
        1 * visitor.startVisit(FileCollectionInternal.OTHER, { it as List == [two] }) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, { it as List == [two] })
        0 * _
    }

    def "can visit structure when collection contains paths and collections"() {
        given:
        def visitor = Mock(FileCollectionStructureVisitor)
        def fileCollectionMock = Mock(FileCollectionInternal)
        def file = new File("some-file")

        when:
        collection.from("file")
        collection.from(fileCollectionMock)

        then:
        1 * fileCollectionMock.replace(_, _) >> fileCollectionMock

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(_, collection) >> true
        1 * fileResolver.resolve("file") >> file
        1 * visitor.startVisit(_, _) >> true
        1 * visitor.visitCollection(_, { it.toList() == [file] })
        1 * fileCollectionMock.visitStructure(visitor)
        0 * visitor._
    }

    def "resolves path to file when finalized"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.finalizeValue()

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        files as List == [file]

        and:
        0 * fileResolver._
    }

    def "resolves closure to files when finalized"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def closure = Mock(Closure)
        collection.from(closure)

        when:
        collection.finalizeValue()

        then:
        1 * closure.call() >> ['a', 'b']
        0 * closure._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2

        when:
        def files = collection.files

        then:
        files as List == [file1, file2]

        and:
        0 * closure._
        0 * fileResolver._
    }

    def "resolves collection to files when finalized"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def collection = Mock(Collection)
        this.collection.from(collection)

        when:
        this.collection.finalizeValue()

        then:
        1 * collection.iterator() >> ['a', 'b'].iterator()
        0 * collection._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2

        when:
        def files = this.collection.files

        then:
        files as List == [file1, file2]

        and:
        0 * collection._
        0 * fileResolver._
    }

    def "cannot specify paths when finalized"() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValue()

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.setFrom()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> is final and cannot be changed any further.'
    }

    def "cannot mutate from set when finalized"() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValue()

        when:
        collection.from.clear()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from.add('b')

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from.remove('a')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from.iterator().remove()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display> is final and cannot be changed any further.'
    }

    def "cannot add paths when finalized"() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValue()

        when:
        collection.from('more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed any further.'
    }

    def "resolves path to file when queried after implicitly finalized"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.implicitFinalizeValue()

        then:
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        then:
        files as List == [file]

        when:
        def files2 = collection.files

        then:
        files2 as List == [file]

        and:
        0 * fileResolver._
    }

    def "resolves closure to files when queried after implicitly finalized"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def closure = Mock(Closure)
        collection.from(closure)

        when:
        collection.implicitFinalizeValue()

        then:
        0 * closure._
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        files as List == [file1, file2]

        then:
        1 * closure.call() >> ['a', 'b']
        0 * closure._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2

        when:
        def files2 = collection.files

        then:
        files2 as List == [file1, file2]

        and:
        0 * closure._
        0 * fileResolver._
    }

    def "resolves collection to files when queried after implicitly finalized"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def collection = Mock(Collection)
        this.collection.from(collection)

        when:
        this.collection.implicitFinalizeValue()

        then:
        0 * collection._
        0 * fileResolver._

        when:
        def files = this.collection.files

        then:
        files as List == [file1, file2]

        then:
        1 * collection.iterator() >> ['a', 'b'].iterator()
        0 * collection._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2

        when:
        def files2 = this.collection.files

        then:
        files2 as List == [file1, file2]

        and:
        0 * collection._
        0 * fileResolver._
    }

    def "throws exception when changes to paths after queried when implicitly finalized"() {
        given:
        def file1 = new File('a')
        def file2 = new File('b')
        collection.from('a')
        _ * fileResolver.resolve('a') >> file1
        _ * fileResolver.resolve('b') >> file2

        when:
        collection.implicitFinalizeValue()
        collection.from('b')

        then:
        collection.files as List == [file1, file2]

        when:
        collection.setFrom('some', 'more')

        then:
        thrown IllegalStateException

        when:
        collection.setFrom(['some', 'more'])

        then:
        thrown IllegalStateException
    }

    def "throws exception when additions to paths after queried when implicitly finalized"() {
        given:
        def file1 = new File('a')
        def file2 = new File('b')
        collection.from('a')
        _ * fileResolver.resolve('a') >> file1
        _ * fileResolver.resolve('b') >> file2

        when:
        collection.implicitFinalizeValue()
        collection.from('b')

        then:
        collection.files as List == [file1, file2]

        when:
        collection.from('some', 'more')

        then:
        thrown IllegalStateException
    }

    def "throws exception when mutations of from set after queried when implicitly finalized"() {
        given:
        def file1 = new File('a')
        def file2 = new File('b')
        collection.from('a')
        _ * fileResolver.resolve('a') >> file1
        _ * fileResolver.resolve('b') >> file2

        when:
        collection.implicitFinalizeValue()
        collection.from('b')

        then:
        collection.files as List == [file1, file2]

        when:
        collection.from.clear()

        then:
        thrown IllegalStateException

        when:
        collection.from.add('c')

        then:
        thrown IllegalStateException

        when:
        collection.from.remove('a')

        then:
        thrown IllegalStateException

        when:
        collection.from.iterator().remove()

        then:
        thrown IllegalStateException
    }

    def "resolves path to file when queried after finalize on read"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.finalizeValueOnRead()

        then:
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        then:
        files as List == [file]

        when:
        def files2 = collection.files

        then:
        files2 as List == [file]

        and:
        0 * fileResolver._
    }

    def "resolves closure to files when queried after finalize on read"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def closure = Mock(Closure)
        collection.from(closure)

        when:
        collection.finalizeValueOnRead()

        then:
        0 * closure._
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        files as List == [file1, file2]

        then:
        1 * closure.call() >> ['a', 'b']
        0 * closure._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2

        when:
        def files2 = collection.files

        then:
        files2 as List == [file1, file2]

        and:
        0 * closure._
        0 * fileResolver._
    }

    def "resolves collection to files when queried after finalize on read"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def collection = Mock(Collection)
        this.collection.from(collection)

        when:
        this.collection.finalizeValueOnRead()

        then:
        0 * collection._
        0 * fileResolver._

        when:
        def files = this.collection.files

        then:
        files as List == [file1, file2]

        then:
        1 * collection.iterator() >> ['a', 'b'].iterator()
        0 * collection._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2

        when:
        def files2 = this.collection.files

        then:
        files2 as List == [file1, file2]

        and:
        0 * collection._
        0 * fileResolver._
    }

    def "can specify paths before queried and finalize on read"() {
        given:
        def file = new File('one')
        collection.finalizeValueOnRead()

        when:
        collection.setFrom('a')

        then:
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        then:
        files as List == [file]
    }

    def "can add paths before queried and finalize on read"() {
        given:
        def file = new File('one')
        collection.finalizeValueOnRead()

        when:
        collection.from('a')

        then:
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        then:
        files as List == [file]
    }

    def "can mutate from set before queried and finalize on read"() {
        given:
        def file = new File('one')
        collection.finalizeValueOnRead()

        when:
        collection.from.add('a')

        then:
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        then:
        files as List == [file]
    }

    def "can visit structure and children after finalized from paths"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        _ * fileResolver.resolve(file1) >> file1
        _ * fileResolver.resolve(file2) >> file2

        collection.from(file1, file2)
        collection.finalizeValue()

        def structureVisitor = Mock(FileCollectionStructureVisitor)
        def childVisitor = Mock(Consumer)

        when:
        collection.visitStructure(structureVisitor)

        then:
        1 * structureVisitor.startVisit(_, collection) >> true
        1 * structureVisitor.startVisit(_, _) >> { source, files ->
            assert files.toList() == [file1]
            true
        }
        1 * structureVisitor.visitCollection(_, _) >> { source, files ->
            assert files.toList() == [file1]
        }
        1 * structureVisitor.startVisit(_, _) >> { source, files ->
            assert files.toList() == [file2]
            true
        }
        1 * structureVisitor.visitCollection(_, _) >> { source, files ->
            assert files.toList() == [file2]
        }
        0 * structureVisitor._

        when:
        collection.visitChildren(childVisitor)

        then:
        2 * childVisitor.accept(_)
        0 * childVisitor._
    }

    def "visiting structure and children does nothing when empty after finalization"() {
        given:
        def files1 = Mock(FileCollectionInternal)
        def files2 = Mock(FileCollectionInternal)

        collection.from(files1, files2)
        collection.finalizeValue()

        def structureVisitor = Mock(FileCollectionStructureVisitor)
        def childVisitor = Mock(Consumer)

        when:
        collection.visitStructure(structureVisitor)

        then:
        1 * structureVisitor.startVisit(_, collection) >> true
        0 * structureVisitor._

        when:
        collection.visitChildren(childVisitor)

        then:
        0 * childVisitor._
    }

    def "cannot specify paths when queried after finalize on read"() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValueOnRead()
        collection.files

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed any further.'
    }

    def "cannot add paths when queried after finalize on read"() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValueOnRead()
        collection.files

        when:
        collection.from('more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from()

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed any further.'
    }

    def "cannot mutate from set when queried after finalize on read"() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValueOnRead()
        collection.files

        when:
        collection.from.clear()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from.add('b')

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from.remove('a')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> is final and cannot be changed any further.'

        when:
        collection.from.iterator().remove()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display> is final and cannot be changed any further.'
    }

    def "cannot specify paths when changes disallowed"() {
        given:
        collection.from('a')

        collection.disallowChanges()

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed any further.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> cannot be changed any further.'
    }

    def "cannot mutate from set when changes disallowed"() {
        given:
        collection.from('a')

        collection.disallowChanges()

        when:
        collection.from.clear()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed any further.'

        when:
        collection.from.add('b')

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> cannot be changed any further.'

        when:
        collection.from.remove('a')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> cannot be changed any further.'

        when:
        collection.from.iterator().remove()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display> cannot be changed any further.'
    }

    def "cannot add paths when changes disallowed"() {
        given:
        collection.from('a')

        collection.disallowChanges()

        when:
        collection.from('more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed any further.'
    }

    def "cannot specify paths when changes disallowed and implicitly finalized"() {
        given:
        collection.from('a')

        collection.disallowChanges()
        collection.implicitFinalizeValue()

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed any further.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> cannot be changed any further.'
    }

    def "resolves closure to files when changes disallowed"() {
        given:
        def file1 = new File('one')
        def file2 = new File('two')
        def closure = Mock(Closure)
        collection.from(closure)

        when:
        collection.disallowChanges()

        then:
        0 * closure._
        0 * fileResolver._

        when:
        def files = collection.files

        then:
        files as List == [file1, file2]

        and:
        1 * closure.call() >> ['a', 'b']
        0 * closure._
        1 * fileResolver.resolve('a') >> file1
        1 * fileResolver.resolve('b') >> file2
        0 * fileResolver._
    }

    def "can finalize when already finalized"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.finalizeValue()

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        when:
        collection.finalizeValue()

        then:
        0 * fileResolver._
    }

    def "can implicitly finalize when already finalized"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.finalizeValue()

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        when:
        collection.implicitFinalizeValue()

        then:
        0 * fileResolver._
    }

    def "can finalize when already implicitly finalized but not queried"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.implicitFinalizeValue()

        then:
        0 * fileResolver._

        when:
        collection.finalizeValue()

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._
    }

    def "can finalize when already implicitly finalized"() {
        given:
        def file = new File('one')
        collection.from('a')

        when:
        collection.implicitFinalizeValue()

        then:
        0 * fileResolver._

        when:
        collection.files

        then:
        1 * fileResolver.resolve('a') >> file
        0 * fileResolver._

        when:
        collection.finalizeValue()

        then:
        0 * fileResolver._
    }

    def "cannot query files when unsafe reads disallowed and host is not ready"() {
        given:
        def file = new File('one')
        collection.from('a')
        collection.disallowUnsafeRead()

        when:
        collection.files

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of <display> because <reason>."

        and:
        1 * host.beforeRead(null) >> "<reason>"
        0 * _

        when:
        def result = collection.files

        then:
        result == [file] as Set

        and:
        1 * host.beforeRead(null) >> null
        1 * fileResolver.resolve('a') >> file
        0 * _
    }

    def "cannot query elements when unsafe reads disallowed and host is not ready"() {
        given:
        def file = new File('one')
        collection.from('a')
        collection.disallowUnsafeRead()

        when:
        def elements = collection.elements

        then:
        0 * _

        when:
        elements.get()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value of <display> because <reason>."

        and:
        1 * host.beforeRead(null) >> "<reason>"
        0 * _

        when:
        def result = elements.get()

        then:
        result.asFile == [file]

        and:
        1 * host.beforeRead(null) >> null
        1 * fileResolver.resolve('a') >> file
        0 * _
    }

    def "cannot finalize value when unsafe reads disallowed and host is not ready"() {
        given:
        def file = new File('one')
        collection.from('a')
        collection.disallowUnsafeRead()

        when:
        collection.finalizeValue()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot finalize the value of <display> because <reason>."

        and:
        1 * host.beforeRead(null) >> "<reason>"
        0 * _

        when:
        collection.finalizeValue()

        then:
        1 * host.beforeRead(null) >> null
        1 * fileResolver.resolve('a') >> file
        0 * _

        when:
        def result = collection.files

        then:
        result == [file] as Set

        and:
        0 * _
    }

    def "can finalize on next read when unsafe reads disallowed and host is not ready"() {
        given:
        def file = new File('one')
        collection.from('a')
        collection.disallowUnsafeRead()

        when:
        collection.finalizeValueOnRead()

        then:
        0 * _

        when:
        def result = collection.files

        then:
        1 * host.beforeRead(null) >> null
        1 * fileResolver.resolve('a') >> file
        0 * _

        then:
        result == [file] as Set
    }

    def "can disallow changes when unsafe reads disallowed and host is not ready"() {
        given:
        def file = new File('one')
        collection.from('a')
        collection.disallowUnsafeRead()

        when:
        collection.disallowChanges()

        then:
        0 * _

        when:
        def result = collection.files

        then:
        1 * host.beforeRead(null) >> null
        1 * fileResolver.resolve('a') >> file
        0 * _

        then:
        result == [file] as Set
    }

    def "can replace one of the elements of an empty collection"() {
        expect:
        def replaced = collection.replace(Stub(FileCollectionInternal), {})
        replaced.is(collection)
    }

    def "can replace one of the elements of a mutable collection"() {
        def collection1 = Mock(FileCollectionInternal)
        def collection2 = Mock(FileCollectionInternal)
        def replaced1 = Stub(FileCollectionInternal)
        def supplier = Stub(Supplier)

        collection.from(collection1, collection2)

        when:
        def replaced = collection.replace(collection1, supplier)

        then:
        replaced != collection
        replaced.sourceCollections == [replaced1, collection2]

        1 * collection1.replace(collection1, supplier) >> replaced1
        1 * collection2.replace(collection1, supplier) >> collection2
        0 * _
    }

    def "can replace one of the elements of a finalized collection"() {
        def collection1 = Stub(FileCollectionInternal)
        def collection2 = Stub(FileCollectionInternal)

        collection.from(collection1, collection2)
        collection.finalizeValue()

        expect:
        def replaced = collection.replace(collection1, {})
        replaced.is(collection)
    }

    def "can clear the collection via #action"() {
        given:
        collection.setFrom('file')

        when:
        actOn(collection)
        def result = collection.files

        then:
        result == [] as Set

        and:
        0 * fileResolver.resolve('file')

        where:
        action         | actOn
        'setFrom()'    | { it.setFrom() }
        'setFrom([])'  | { it.setFrom([]) }
        'setFrom(*[])' | { it.setFrom(*[]) }
    }

    def "can obtain shallow copy of #description collection"() {
        given:
        configuration.setDelegate(this)
        configuration.setResolveStrategy(Closure.DELEGATE_ONLY)
        configuration(collection)

        when:
        def copy = collection.shallowCopy()

        then:
        copy.files == collection.files

        where:
        description      | configuration
        "empty"          | {}
        "single element" | { it.setFrom(containing(new File("file"))) }
        "multi-element"  | { it.setFrom(containing(new File("file1"))); it.from(containing(new File("file2"))) }
        "finalized"      | { it.setFrom(containing(new File("file"))); it.finalizeValue() }
    }

    def "shallow copy of #description collection does not follow changes to original"() {
        given:
        configuration.setDelegate(this)
        configuration.setResolveStrategy(Closure.DELEGATE_ONLY)
        configuration(collection)

        when:
        def copy = collection.shallowCopy()
        collection.setFrom(containing(new File("other")))

        then:
        copy.files != collection.files
        copy.files == expectedCopyContents as Set<File>

        where:
        description      | expectedCopyContents                             | configuration
        "empty"          | [] as File[]                                     | {}
        "single element" | [new File("file")] as File[]                     | { it.setFrom(containing(expectedCopyContents)) }
        "multi-element"  | [new File("file1"), new File("file2")] as File[] | { it.setFrom(containing(expectedCopyContents[0])); it.from(containing(expectedCopyContents[1])) }
    }

    def "shallow copy inherits dependencies of the original"() {
        given:
        def task = Mock(Task)
        collection.builtBy("a")

        when:
        def copyDeps = collection.shallowCopy().buildDependencies.getDependencies(null)

        then:
        copyDeps == [task] as Set<Task>

        _ * taskResolver.resolveTask("a") >> task
    }

    def "shallow copy does not follow changes to dependencies of the original"() {
        given:
        def task = Mock(Task)
        collection.builtBy("a")

        when:
        def copy = collection.shallowCopy()
        collection.builtBy("b")

        def copyDeps = copy.buildDependencies.getDependencies(null)

        then:
        copyDeps == [task] as Set<Task>

        _ * taskResolver.resolveTask("a") >> task
    }

    def "shallow copy reflects changes to inner collection"() {
        given:
        def inner = new DefaultConfigurableFileCollection("<display>", fileResolver, taskDependencyFactory, patternSetFactory, host).from(containing(new File("a")))
        collection.from(inner)

        when:
        def copy = collection.shallowCopy()
        inner.from(containing(new File("b")))
        collection.from(containing(new File("c")))

        then:
        copy.files == [new File("a"), new File("b")] as Set<File>
    }

    def "replace can modify contents of the collection"() {
        given:
        def a = new File("a.txt")
        def b = new File("b.md")
        collection.from(containing(a, b))

        when:
        collection.replace { it.filter { f -> !f.name.endsWith(".txt") } }

        then:
        collection.files == [b] as Set<File>
    }

    def "replace is not applied to later collection modifications"() {
        given:
        def a = new File("a.txt")
        def b = new File("b.md")
        def c = new File("c.txt")
        collection.from(containing(a, b))

        when:
        collection.replace { it.filter { f -> !f.name.endsWith(".txt") } }
        collection.from(containing(c))

        then:
        collection.files == [b, c] as Set<File>
    }

    def "replace argument is live"() {
        given:
        def a = new File("a.txt")
        def b = new File("b.md")
        def c = new File("c.txt")
        def d = new File("d.md")

        def upstream = new DefaultConfigurableFileCollection("<display>", fileResolver, taskDependencyFactory, patternSetFactory, host).from(containing(a, b))
        collection.from(upstream)
        when:
        collection.replace { it.filter { f -> !f.name.endsWith(".txt") } }
        upstream.from(containing(c, d))

        then:
        collection.files == [b, d] as Set<File>
    }

    def "returning null from replace clears collection"() {
        given:
        collection.from(containing(new File("a.txt")))

        when:
        collection.replace { null }

        then:
        collection.isEmpty()
    }

    def "replace transformation runs eagerly"() {
        given:
        Transformer<FileCollection, FileCollection> transform = Mock()
        collection.from(containing(new File("a.txt")))

        when:
        collection.replace(transform)

        then:
        1 * transform.transform(_)
    }

    def "replace transformation result is evaluated lazily"() {
        given:
        Spec<File> filterSpec = Mock()
        collection.from(containing(new File("a.txt")))

        when:
        collection.replace { it.filter(filterSpec) }

        then:
        0 * filterSpec._
    }

    def "can set paths as convention to the collection"() {
        when:
        collection.convention("src1", "src2")
        then:
        collection.from as List == ["src1", "src2"]
        !collection.explicit
    }

    def "can incrementally set paths using action"() {
        when:
        collection.withActualValue({
            it.from("src1", "src2")
            it.from("src3")
        })
        then:
        collection.from as List == ["src1", "src2", "src3"]
    }

    def "can incrementally set paths using closure"() {
        when:
        collection.withActualValue {
            it.from("src1", "src2")
            it.from("src3")
        }
        then:
        collection.from as List == ["src1", "src2", "src3"]
    }

    def "can incrementally set paths as convention to the collection"() {
        when:
        collection.convention("src0")
        collection.withActualValue {
            it.from("src1", "src2")
            it.from("src3")
        }
        then:
        collection.from as List == ["src0", "src1", "src2", "src3"]
        collection.explicit
    }

    def "can incrementally set paths as convention to the collection using from"() {
        when:
        collection.convention("src0")
        collection.from("src1", "src2")
        collection.from("src3")
        then:
        collection.from as List == ["src0", "src1", "src2", "src3"]
        collection.explicit
    }

    def "can incrementally set explicit value"() {
        when:
        collection.setFrom("src1")
        collection.convention("unused-src")
        collection.withActualValue {
            it.from("src3")
            it.from("src4")
        }
        then:
        collection.from as List == ["src1", "src3", "src4"]
    }

    def "can unset convention"() {
        given:
        collection.convention("src0")
        assert !collection.explicit

        expect:
        collection.setFrom("src1")
        assert collection.explicit

        when:
        collection.unset()

        then:
        assert !collection.explicit
        collection.from as List == ["src0"]

        when:
        collection.unsetConvention()

        then:
        collection.from as List == []
    }

    def "can set convention as explicit value"() {
        given:
        collection.convention("src0")

        expect:
        !collection.explicit

        when:
        collection.setFrom("src1")

        then:
        collection.from as List == ["src1"]

        when:
        collection.setToConvention()

        then:
        assert collection.explicit
        collection.from as List == ["src0"]
    }

    def "can set convention as explicit value if unset"() {
        given:
        collection.convention("src0")

        when:
        collection.setFrom("src1")

        then:
        collection.from as List == ["src1"]

        when:
        collection.setToConventionIfUnset()

        then:
        collection.from as List == ["src1"]

        when:
        collection.unset()

        then:
        assert !collection.explicit

        when:
        collection.setToConventionIfUnset()

        then:
        assert collection.explicit
        collection.from as List == ["src0"]
    }

    def "test '#label'"(String label) {
        given:
        if (!(convention instanceof Wildcard)) {
            collection.convention((Iterable) convention)
        }
        if (!(explicit instanceof Wildcard)) {
            collection.setFrom((Iterable) explicit)
        }

        when:
        operations.each {operation -> operation.call(collection) }

        then:
        collection.from.flatten() as List == expected

        where:
        expected        | explicit      | convention        | label                                         | operations
        []              | _             | _                 | "no elements by default"                      | { }
        ["src1"]        | ["src1"]      | _                 | "explicit value when set"                     | { }
        ["src1"]        | _             | ["src1"]          | "convention used when no explicit value"      | { }
        ["src3"]        | ["src3"]      | ["src1"]          | "explicit value overrides convention"         | { }
        ["src1"]        | ["src3"]      | ["src1"]          | "convention used when explicit unset"         | { it.unset() }
        ["src1", "src2"]| _             | _                 | "from() after convention honors it"           | { it.convention("src1"); it.from("src2") }
        ["src2"]        | _             | _                 | "from() before convention prevents it"        | { it.from("src2"); it.convention("src1") }
        ["src1", "src2"]| _             | ["src1"]          | "from() commits convention"                   | { it.from("src2"); it.unsetConvention() }
        ["src1"]        | _             | ["src1"]          | "from() does not modify convention"           | { it.from("src2"); it.unset() }
    }
}
