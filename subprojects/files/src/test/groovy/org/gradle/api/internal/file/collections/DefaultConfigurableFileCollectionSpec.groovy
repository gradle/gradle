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
import org.gradle.testing.internal.util.Specification
import org.gradle.util.UsesNativeServices

import java.util.concurrent.Callable

@UsesNativeServices
class DefaultConfigurableFileCollectionSpec extends Specification {

    def resolverMock = Mock(FileResolver)
    def taskResolverStub = Mock(TaskResolver)
    def collection = new DefaultConfigurableFileCollection(resolverMock, taskResolverStub)

    def canCreateEmptyCollection() {
        expect:
        collection.from.empty
        collection.files.empty
    }

    def resolvesSpecifiedFilesUseFileResolver() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        DefaultConfigurableFileCollection collection = new DefaultConfigurableFileCollection(resolverMock, taskResolverStub, Arrays.asList("a", "b"))
        def from = collection.from
        def files = collection.files

        then:
        1 * resolverMock.resolve("a") >> file1
        1 * resolverMock.resolve("b") >> file2
        from as List == ["a", "b"]
        files as List == [file1, file2]
    }

    def canAddPathsToTheCollection() {
        when:
        collection.from("src1", "src2")
        then:
        collection.getFrom() as List == ["src1", "src2"]
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
        collection.getFrom() == [["a", "b"]] as Set
    }

    def resolvesSpecifiedPathsUseFileResolver() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        DefaultConfigurableFileCollection collection = new DefaultConfigurableFileCollection(resolverMock, taskResolverStub, Arrays.asList("src1", "src2"))
        def files = collection.getFiles()

        then:
        1 * resolverMock.resolve("src1") >> file1
        1 * resolverMock.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def canUseAClosureToSpecifyTheContentsOfTheCollection() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        def paths = ["a"]
        collection.from({ paths })
        def files = collection.getFiles()

        then:
        1 * resolverMock.resolve("a") >> file1
        files == [file1] as Set

        when:
        paths.add("b")
        files = collection.getFiles()

        then:
        1 * resolverMock.resolve("a") >> file1
        1 * resolverMock.resolve("b") >> file2
        files as List == [file1, file2]
    }

    def canUseAClosureToSpecifyASingleFile() {
        given:
        def file = new File("1")

        when:
        collection.from({ 'a' as Character })
        def files = collection.getFiles()

        then:
        1 * resolverMock.resolve('a' as Character) >> file
        files == [file] as Set
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
        1 * resolverMock.resolve("src1") >> file1
        files == [file1] as Set

        when:
        paths.add("src2")
        files = collection.files

        then:
        1 * resolverMock.resolve("src1") >> file1
        1 * resolverMock.resolve("src2") >> file2
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
        1 * resolverMock.resolve("src1") >> file1
        1 * resolverMock.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def canUseNestedObjectsToSpecifyTheContentsOfTheCollection() {
        given:
        def file1 = new File("1")
        def file2 = new File("2")

        when:
        collection.from({[{['src1', { ['src2'] as String[] }]}]})
        def files = collection.files

        then:
        1 * resolverMock.resolve("src1") >> file1
        1 * resolverMock.resolve("src2") >> file2
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
        files == [file1] as Set

        when:
        files = collection.files

        then:
        1 * src.getFiles() >> ([file1, file2] as LinkedHashSet)
        files == [file1, file2] as LinkedHashSet
    }

    def canUseACallableToSpecifyTheContentsOfTheCollection() throws Exception {
        given:
        def file1 = new File("1")
        def file2 = new File("2")
        def callable = Mock(Callable)

        when:
        collection.from(callable)
        def files = collection.files

        then:
        1 * callable.call() >> ["src1", "src2"]
        _ * resolverMock.resolve("src1") >> file1
        _ * resolverMock.resolve("src2") >> file2
        files as List == [file1, file2]
    }

    def callableCanReturnNull() throws Exception {
        given:
        def callable = Mock(Callable)

        when:
        collection.from(callable)
        def files = collection.files

        then:
        1 * callable.call() >> null
        0 * resolverMock._
        files.empty
    }

    def resolveAddsEachSourceObjectAndBuildDependencies() {
        given:
        def resolveContext = Mock(FileCollectionResolveContext)
        def nestedContext = Mock(FileCollectionResolveContext)
        def fileCollectionMock = Mock(FileCollection)

        when:
        collection.from("file")
        collection.from(fileCollectionMock)
        collection.visitContents(resolveContext)

        then:
        1 * resolveContext.push(resolverMock) >> nestedContext
        1 * nestedContext.add(collection.from)
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
        _ * resolverMock.resolve("f") >> new File("f")
        _ * taskResolverStub.resolveTask("c") >> task
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
        _ * resolverMock.resolve("f") >> new File("f")
        _ * fileCollectionMock.getBuildDependencies() >> dependency
        _ * dependency.getDependencies(null) >> ([taskA] as Set)
        _ * taskResolverStub.resolveTask("b") >> taskB
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
        _ * taskResolverStub.resolveTask("task") >> task
        dependencies == [task] as Set<? extends Task>
        fileTreeDependencies == [task] as Set<? extends Task>
        filteredFileTreeDependencies == [task] as Set<? extends Task>
    }

}
