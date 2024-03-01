/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.AbstractFileCollectionTest.TestFileCollection
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Consumer

@UsesNativeServices
class CompositeFileCollectionTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def file1 = new File("1")
    def file2 = new File("2")
    def file3 = new File("3")

    void "contains union of all source collections"() {
        def source1 = new TestFileCollection(file1, file2)
        def source2 = new TestFileCollection(file2, file3)
        def collection = new TestCompositeFileCollection(source1, source2)

        expect:
        collection.getFiles() as List == [file1, file2, file3]
    }

    def "contents track contents of source collections"() {
        def source1 = new TestFileCollection(file1)
        def source2 = new TestFileCollection(file2, file3)
        def collection = new TestCompositeFileCollection(source1, source2)

        expect:
        collection.getFiles() as List == [file1, file2, file3]
    }

    def "contains file when at least one source collection contains file"() {
        def source1 = new TestFileCollection(file1)
        def source2 = new TestFileCollection(file2)
        def collection = new TestCompositeFileCollection(source1, source2)

        expect:
        collection.contains(file2)
    }

    def "does not contain file when no source collection contains file"() {
        def source1 = new TestFileCollection(file1)
        def source2 = new TestFileCollection(file2)
        def collection = new TestCompositeFileCollection(source1, source2)

        expect:
        !collection.contains(file3)
    }

    def "is empty when has no source collections"() {
        expect:
        new TestCompositeFileCollection().isEmpty()
    }

    def "is empty when all source collections are empty"() {
        def source1 = new TestFileCollection()
        def source2 = new TestFileCollection()
        def collection = new TestCompositeFileCollection(source1, source2)

        expect:
        collection.isEmpty()
    }

    def "isn't empty when any source collection is not empty"() {
        def source1 = new TestFileCollection()
        def source2 = new TestFileCollection(file2)
        def collection = new TestCompositeFileCollection(source1, source2)

        expect:
        !collection.isEmpty()
    }

    def "add to Ant builder delegates to each source collection"() {
        def source1 = Mock(AbstractFileCollection)
        def source2 = Mock(AbstractFileCollection)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        collection.addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection)

        then:
        1 * source1.addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection)
        1 * source2.addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection)
        0 * _
    }

    void "visits children when structure is visited"() {
        def visitor = Mock(FileCollectionStructureVisitor)
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(_, collection) >> true
        1 * source1.visitStructure(visitor)
        1 * source2.visitStructure(visitor)
        0 * _
    }

    void "listener can skip visiting children"() {
        def visitor = Mock(FileCollectionStructureVisitor)
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(_, collection) >> false
        0 * _
    }

    def "getAsFiletrees() returns union of file trees"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def source1 = Mock(FileTreeInternal)
        def source2 = Mock(FileTreeInternal)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def trees = collection.getAsFileTrees()

        then:
        trees.size() == 2
        trees[0].dir == dir1
        trees[1].dir == dir2

        1 * source1.visitStructure(_) >> { FileCollectionStructureVisitor visitor ->
            visitor.visitFileTree(dir1, Stub(PatternSet), source1)
        }
        1 * source2.visitStructure(_) >> { FileCollectionStructureVisitor visitor ->
            visitor.visitFileTree(dir2, Stub(PatternSet), source2)
        }
        0 * _
    }

    def "getAsFileTree() delegates to each source collection"() {
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def fileTree = collection.getAsFileTree()

        then:
        0 * _

        when:
        fileTree.visitContentsAsFileTrees({})

        then:
        1 * source1.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, []) }
        1 * source2.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, []) }
        0 * _
    }

    def "file tree is live"() {
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def dir1 = new File("dir1")
        def dir2 = new File("dir1")
        def dir3 = new File("dir1")
        def source3 = Mock(FileCollectionInternal)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def fileTree = collection.getAsFileTree()

        then:
        0 * _

        when:
        fileTree.visitContentsAsFileTrees({})

        then:
        1 * source1.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [dir1]) }
        1 * source2.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [dir2]) }
        0 * _

        when:
        collection.sourceCollections.add(source3)
        fileTree.visitContentsAsFileTrees({})

        then:
        1 * source1.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [dir1]) }
        1 * source2.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [dir2]) }
        1 * source3.visitStructure(_) >> { FileCollectionStructureVisitor visitor -> visitor.visitCollection(null, [dir3]) }
        0 * _
    }

    def "can filter the elements of collection"() {
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def spec = Stub(Spec)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def filtered = collection.filter(spec)

        then:
        0 * _

        and:
        filtered instanceof FilteredFileCollection
    }

    def "can replace backing collection of filtered collection"() {
        def source1 = Mock(FileCollectionInternal)
        def source2 = Mock(FileCollectionInternal)
        def other = Mock(FileCollectionInternal)
        def spec = Stub(Spec)
        def collection = new TestCompositeFileCollection(source1, source2)
        def collection2 = new TestCompositeFileCollection(source2)

        def filtered = collection.filter(spec)

        when:
        def replaced = filtered.replace(other, {})
        def replaced2 = filtered.replace(collection, { collection2 })

        then:
        replaced.is(filtered)
        replaced2 != filtered
        0 * _
    }

    private class TestCompositeFileCollection extends CompositeFileCollection {
        List<Object> sourceCollections

        TestCompositeFileCollection(FileCollectionInternal... sourceCollections) {
            this.sourceCollections = sourceCollections as List
        }

        @Override
        String getDisplayName() {
            "<display name>"
        }

        @Override
        protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
            sourceCollections.forEach {
                visitor.accept(it)
            }
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
            throw new UnsupportedOperationException()
        }
    }
}
