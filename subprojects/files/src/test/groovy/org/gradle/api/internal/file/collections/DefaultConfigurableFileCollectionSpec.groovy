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
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.TaskResolver
import org.gradle.api.tasks.TaskDependency
import spock.lang.Specification

import java.util.concurrent.Callable

class DefaultConfigurableFileCollectionSpec extends Specification {

    def fileResolver = Mock(FileResolver)
    def taskResolver = Mock(TaskResolver)
    def collection = new DefaultConfigurableFileCollection("<display>", fileResolver, taskResolver)

    def canCreateEmptyCollection() {
        expect:
        collection.from.empty
        collection.files.empty
    }

    def resolvesSpecifiedFilesUsingFileResolver() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        DefaultConfigurableFileCollection collection = new DefaultConfigurableFileCollection(fileResolver, taskResolver, ["a", "b"])
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

        when:
        collection.from.clear()

        then:
        collection.from.empty

        when:
        collection.from.add('a')

        then:
        collection.from as List == ['a']
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
        def dependency = Mock(TaskDependency)

        when:
        collection.from(fileCollectionMock)
        collection.from("f")
        collection.builtBy("b")
        def dependencies = collection.getBuildDependencies().getDependencies(null)

        then:
        _ * fileResolver.resolve("f") >> new File("f")
        _ * fileCollectionMock.getBuildDependencies() >> dependency
        _ * dependency.getDependencies(null) >> ([taskA] as Set)
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
        // TODO - remove this
        1 * fileResolver.resolve(file) >> file
        0 * fileResolver._
        files as List == [file]
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
        0 * closure._
        // TODO - remove these resolves
        1 * fileResolver.resolve(file1) >> file1
        1 * fileResolver.resolve(file2) >> file2
        0 * fileResolver._
        files as List == [file1, file2]
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
        0 * collection._
        // TODO - remove this
        // TODO - remove these resolves
        1 * fileResolver.resolve(file1) >> file1
        1 * fileResolver.resolve(file2) >> file2
        0 * fileResolver._
        files as List == [file1, file2]
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
}
