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
import org.gradle.api.internal.file.collections.FileCollectionResolveContext
import org.gradle.api.internal.file.collections.MinimalFileSet
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.tasks.util.PatternSet
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

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

    def "getAsFiletrees() returns union of file trees"() {
        def dir1 = tmpDir.createDir("dir1")
        def dir2 = tmpDir.createDir("dir2")
        def source1 = Mock(AbstractFileTree)
        def source2 = Mock(AbstractFileTree)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def trees = collection.getAsFileTrees()

        then:
        trees.size() == 2
        trees[0].dir == dir1
        trees[1].dir == dir2

        1 * source1.visitContents(_) >> { FileCollectionStructureVisitor visitor ->
            visitor.visitFileTree(dir1, Stub(PatternSet), source1)
        }
        1 * source2.visitContents(_) >> { FileCollectionStructureVisitor visitor ->
            visitor.visitFileTree(dir2, Stub(PatternSet), source2)
        }
        0 * _
    }

    def "getAsFileTree() delegates to each source collection"() {
        def source1 = Mock(AbstractFileCollection)
        def source2 = Mock(AbstractFileCollection)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def fileTree = collection.getAsFileTree()

        then:
        0 * _

        when:
        ((CompositeFileTree) fileTree).getSourceCollections()

        then:
        fileTree instanceof CompositeFileTree
        1 * source1.iterator() >> [].iterator()
        1 * source2.iterator() >> [].iterator()
        0 * _
    }

    def "file tree is live"() {
        def source1 = Mock(AbstractFileCollection)
        def source2 = Mock(AbstractFileCollection)
        def dir1 = new File("dir1")
        def dir2 = new File("dir1")
        def dir3 = new File("dir1")
        def source3 = Mock(MinimalFileSet)
        def collection = new TestCompositeFileCollection(source1, source2)

        when:
        def fileTree = collection.getAsFileTree();

        then:
        0 * _

        when:
        ((CompositeFileTree) fileTree).getSourceCollections()

        then:
        fileTree instanceof CompositeFileTree
        1 * source1.iterator() >> [dir1].iterator()
        1 * source2.iterator() >> [dir2].iterator()
        0 * _

        when:
        collection.sourceCollections.add(source3)
        ((CompositeFileTree) fileTree).getSourceCollections()

        then:
        1 * source1.iterator() >> [dir1].iterator()
        1 * source2.iterator() >> [dir2].iterator()
        1 * source3.getFiles() >> ([dir3] as Set)
        0 * _
    }

    private class TestCompositeFileCollection extends CompositeFileCollection {
        List<Object> sourceCollections

        TestCompositeFileCollection(FileCollection... sourceCollections) {
            this.sourceCollections = sourceCollections as List
        }

        @Override
        String getDisplayName() {
            "<display name>"
        }

        @Override
        void visitContents(FileCollectionResolveContext context) {
            context.addAll(sourceCollections)
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
            throw new UnsupportedOperationException()
        }
    }
}
