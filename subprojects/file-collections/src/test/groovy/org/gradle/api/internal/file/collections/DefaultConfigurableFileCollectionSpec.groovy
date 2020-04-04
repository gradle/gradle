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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.AbstractFileCollection
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileCollectionSpec
import org.gradle.api.internal.file.FileCollectionStructureVisitor
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.api.internal.tasks.DefaultTaskDependency
import org.gradle.api.internal.tasks.TaskDependencyFactory
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.internal.tasks.TaskResolver

import java.util.concurrent.Callable

class DefaultConfigurableFileCollectionSpec extends FileCollectionSpec {

    def fileResolver = Mock(FileResolver)
    def taskResolver = Mock(TaskResolver)
    def host = Mock(PropertyHost)
    def patternSetFactory = TestFiles.patternSetFactory
    def taskDependencyFactory = Stub(TaskDependencyFactory) {
        _ * configurableDependency() >> new DefaultTaskDependency(taskResolver)
    }
    def collection = new DefaultConfigurableFileCollection("<display>", fileResolver, taskDependencyFactory, patternSetFactory, host)

    @Override
    AbstractFileCollection containing(File... files) {
        def resolver = Stub(FileResolver)
        _ * resolver.resolve(_) >> { File f -> f }
        return new DefaultConfigurableFileCollection("<display>", resolver, taskDependencyFactory, patternSetFactory, host).from(files)
    }

    def resolvesSpecifiedFilesUsingFileResolver() {
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

    def canAddPathsToTheCollection() {
        when:
        collection.from("src1", "src2")
        then:
        collection.from as List == ["src1", "src2"]
    }

    def canSetThePathsOfTheCollection() {
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

    def canMutateTheFromCollection() {
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

    def resolvesSpecifiedPathsUsingFileResolver() {
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

    def canUseAClosureToSpecifyTheContentsOfTheCollection() {
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

    def canUseAClosureToSpecifyASingleFile() {
        given:
        def file = new File("1")

        when:
        collection.from({ 'a' as Character })
        def files = collection.files

        then:
        1 * fileResolver.resolve('a' as Character) >> file
        files as List == [file]
    }

    def closureCanReturnNull() {
        when:
        collection.from({ null })

        then:
        collection.files.empty
    }

    def canUseACollectionToSpecifyTheContentsOfTheCollection() {
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

    def canUseAnArrayToSpecifyTheContentsOfTheCollection() {
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

    def canUseNestedObjectsToSpecifyTheContentsOfTheCollection() {
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

    def canUseAFileCollectionToSpecifyTheContentsOfTheCollection() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def src = Mock(FileCollectionInternal)

        when:
        collection.from(src)
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

    def canUseACallableToSpecifyTheContentsOfTheCollection() {
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

    def callableCanReturnNull() {
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

    def elementsProviderTracksChangesToContent() {
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

    def resolveAddsEachSourceObjectAndBuildDependencies() {
        given:
        def resolveContext = Mock(FileCollectionResolveContext)
        def fileCollectionMock = Mock(FileCollection)

        collection.from("file")
        collection.from(fileCollectionMock)

        when:
        collection.visitContents(resolveContext)

        then:
        1 * resolveContext.add("file", fileResolver)
        1 * resolveContext.add(fileCollectionMock)
        0 * resolveContext._
    }

    def canGetAndSetTaskDependencies() {
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
        _ * fileResolver.resolve("f") >> new File("f")
        _ * taskResolver.resolveTask("c") >> task
        dependencies == [task] as Set<? extends Task>
    }

    def taskDependenciesContainsUnionOfDependenciesOfNestedFileCollectionsPlusOwnDependencies() {
        given:
        def fileCollectionMock = Mock(FileCollectionInternal)
        def taskA = Mock(Task)
        def taskB = Mock(Task)

        when:
        collection.from(fileCollectionMock)
        collection.from("f")
        collection.builtBy("b")
        def dependencies = collection.getBuildDependencies().getDependencies(null)

        then:
        _ * fileResolver.resolve("f") >> new File("f")
        _ * fileCollectionMock.visitDependencies(_) >> { TaskDependencyResolveContext context -> context.add(taskA) }
        _ * taskResolver.resolveTask("b") >> taskB
        dependencies == [taskA, taskB] as Set<? extends Task>
    }

    def hasSpecifiedDependenciesWhenEmpty() {
        given:
        def task = Stub(Task)
        collection.builtBy("task")

        when:
        def dependencies = collection.getBuildDependencies().getDependencies(null)
        def fileTreeDependencies = collection.getAsFileTree().getBuildDependencies().getDependencies(null)
        def filteredFileTreeDependencies = collection.getAsFileTree().matching({}).getBuildDependencies().getDependencies(null)

        then:
        _ * taskResolver.resolveTask("task") >> task
        dependencies == [task] as Set<? extends Task>
        fileTreeDependencies == [task] as Set<? extends Task>
        filteredFileTreeDependencies == [task] as Set<? extends Task>
    }

    def "can visit contents when collection contains paths"() {
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
        1 * visitor.startVisit(FileCollectionInternal.OTHER, {it as List == [one]}) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, {it as List == [one]})
        1 * fileResolver.resolve('b') >> two
        1 * visitor.startVisit(FileCollectionInternal.OTHER, {it as List == [two]}) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, {it as List == [two]})
        0 * _
    }

    def resolvesPathToFileWhenFinalized() {
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

    def resolvesClosureToFilesWhenFinalized() {
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

    def resolvesCollectionToFilesWhenFinalized() {
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

    def cannotSpecifyPathsWhenFinalized() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValue()

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed.'
    }

    def cannotMutateFromSetWhenFinalized() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValue()

        when:
        collection.from.clear()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.from.add('b')

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.from.remove('a')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.from.iterator().remove()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display> is final and cannot be changed.'
    }

    def cannotAddPathsWhenFinalized() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValue()

        when:
        collection.from('more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed.'
    }

    def resolvesPathToFileWhenQueriedAfterImplicitlyFinalized() {
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

    def resolvesClosureToFilesWhenQueriedAfterImplicitlyFinalized() {
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

    def resolvesCollectionToFilesWhenQueriedAfterImplicitlyFinalized() {
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

    def ignoresChangesToPathsAfterQueriedWhenImplicitlyFinalized() {
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
        collection.files as List == [file1, file2]

        when:
        collection.setFrom(['some', 'more'])

        then:
        collection.files as List == [file1, file2]
    }

    def ignoresAdditionsToPathsAfterQueriedWhenImplicitlyFinalized() {
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
        collection.files as List == [file1, file2]
    }

    def ignoresMutationsOfFromSetAfterQueriedWhenImplicitlyFinalized() {
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
        collection.files as List == [file1, file2]

        when:
        collection.from.add('c')

        then:
        collection.files as List == [file1, file2]

        when:
        collection.from.remove('a')

        then:
        collection.files as List == [file1, file2]

        when:
        collection.from.iterator().remove()

        then:
        collection.files as List == [file1, file2]
    }

    def resolvesPathToFileWhenQueriedAfterFinalizeOnRead() {
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

    def resolvesClosureToFilesWhenQueriedAfterFinalizeOnRead() {
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

    def resolvesCollectionToFilesWhenQueriedAfterFinalizeOnRead() {
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

    def canSpecifyPathsBeforeQueriedAndFinalizeOnRead() {
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

    def canAddPathsBeforeQueriedAndFinalizeOnRead() {
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

    def canMutateFromSetBeforeQueriedAndFinalizeOnRead() {
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

    def cannotSpecifyPathsWhenQueriedAfterFinalizeOnRead() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValueOnRead()
        collection.files

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed.'
    }

    def cannotAddPathsWhenQueriedAfterFinalizeOnRead() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValueOnRead()
        collection.files

        when:
        collection.from('more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed.'
    }

    def cannotMutateFromSetWhenQueriedAfterFinalizeOnRead() {
        given:
        collection.from('a')
        _ * fileResolver.resolve('a') >> new File('a')

        collection.finalizeValueOnRead()
        collection.files

        when:
        collection.from.clear()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.from.add('b')

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.from.remove('a')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> is final and cannot be changed.'

        when:
        collection.from.iterator().remove()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display> is final and cannot be changed.'
    }

    def cannotSpecifyPathsWhenChangesDisallowed() {
        given:
        collection.from('a')

        collection.disallowChanges()

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> cannot be changed.'
    }

    def cannotMutateFromSetWhenChangesDisallowed() {
        given:
        collection.from('a')

        collection.disallowChanges()

        when:
        collection.from.clear()

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed.'

        when:
        collection.from.add('b')

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> cannot be changed.'

        when:
        collection.from.remove('a')

        then:
        def e3 = thrown(IllegalStateException)
        e3.message == 'The value for <display> cannot be changed.'

        when:
        collection.from.iterator().remove()

        then:
        def e4 = thrown(IllegalStateException)
        e4.message == 'The value for <display> cannot be changed.'
    }

    def cannotAddPathsWhenChangesDisallowed() {
        given:
        collection.from('a')

        collection.disallowChanges()

        when:
        collection.from('more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed.'
    }

    def cannotSpecifyPathsWhenChangesDisallowedAndImplicitlyFinalized() {
        given:
        collection.from('a')

        collection.disallowChanges()
        collection.implicitFinalizeValue()

        when:
        collection.setFrom('some', 'more')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'The value for <display> cannot be changed.'

        when:
        collection.setFrom(['some', 'more'])

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'The value for <display> cannot be changed.'
    }

    def resolvesClosureToFilesWhenChangesDisallowed() {
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

    def canFinalizeWhenAlreadyFinalized() {
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

    def canImplicitlyFinalizeWhenAlreadyFinalized() {
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

    def canFinalizeWhenAlreadyImplicitlyFinalizedButNotQueried() {
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

    def canFinalizeWhenAlreadyImplicitlyFinalized() {
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

    def cannotQueryFilesWhenUnsafeReadsDisallowedAndHostIsNotReady() {
        given:
        def file = new File('one')
        collection.from('a')
        collection.disallowUnsafeRead()

        when:
        collection.files

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot query the value for <display> because <reason>."

        and:
        1 * host.beforeRead() >> "<reason>"
        0 * _

        when:
        def result = collection.files

        then:
        result == [file] as Set

        and:
        1 * host.beforeRead() >> null
        1 * fileResolver.resolve('a') >> file
        0 * _
    }

    def cannotQueryElementsWhenUnsafeReadsDisallowedAndHostIsNotReady() {
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
        e.message == "Cannot query the value for <display> because <reason>."

        and:
        1 * host.beforeRead() >> "<reason>"
        0 * _

        when:
        def result = elements.get()

        then:
        result.asFile == [file]

        and:
        1 * host.beforeRead() >> null
        1 * fileResolver.resolve('a') >> file
        0 * _
    }
}
