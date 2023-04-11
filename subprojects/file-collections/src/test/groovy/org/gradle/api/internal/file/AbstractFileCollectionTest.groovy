/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitorUtil
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskDependencyContainerInternal
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.specs.Spec
import org.gradle.util.internal.GUtil
import org.gradle.util.TestUtil

import static org.gradle.util.Matchers.isEmpty
import static org.gradle.util.internal.WrapUtil.toLinkedSet
import static org.gradle.util.internal.WrapUtil.toList
import static org.gradle.util.internal.WrapUtil.toSet
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.core.IsInstanceOf.instanceOf
import static org.hamcrest.MatcherAssert.assertThat

class AbstractFileCollectionTest extends FileCollectionSpec {
    public final TaskDependencyContainerInternal dependency = Mock(TaskDependencyContainerInternal.class)

    @Override
    AbstractFileCollection containing(File... files) {
        return new TestFileCollection(files)
    }

    void canGetSingleFile() {
        def file = new File("f1")
        def collection = new TestFileCollection(file)

        expect:
        collection.getSingleFile().is(file)
    }

    void failsToGetSingleFileWhenCollectionContainsMultipleFiles() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        TestFileCollection collection = new TestFileCollection(file1, file2)

        expect:
        try {
            collection.getSingleFile()
            fail()
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Expected collection-display-name to contain exactly one file, however, it contains more than one file."))
        }
    }

    void failsToGetSingleFileWhenCollectionIsEmpty() {
        TestFileCollection collection = new TestFileCollection()

        expect:
        try {
            collection.getSingleFile()
            fail()
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Expected collection-display-name to contain exactly one file, however, it contains no files."))
        }
    }

    void containsFile() {
        def file1 = new File("f1")
        def collection = new TestFileCollection(file1)

        expect:
        collection.contains(file1)
        !collection.contains(new File("f2"))
    }

    void canGetFilesAsAPath() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        TestFileCollection collection = new TestFileCollection(file1, file2)

        expect:
        assertThat(collection.getAsPath(), equalTo(file1.path + File.pathSeparator + file2.path))
    }

    void canAddCollectionsTogether() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        TestFileCollection collection1 = new TestFileCollection(file1, file2)
        TestFileCollection collection2 = new TestFileCollection(file2, file3)

        when:
        FileCollection sum = collection1.plus(collection2)

        then:
        assertThat(sum, instanceOf(UnionFileCollection.class))
        assertThat(sum.getFiles(), equalTo(toLinkedSet(file1, file2, file3)))
    }

    def "can add collections using + operator"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        TestFileCollection collection1 = new TestFileCollection(file1, file2)
        TestFileCollection collection2 = new TestFileCollection(file2, file3)

        when:
        FileCollection sum = collection1 + collection2

        then:
        sum instanceof UnionFileCollection
        sum.getFiles() == toLinkedSet(file1, file2, file3)
    }

    def "can add a list of collections"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        TestFileCollection collection1 = new TestFileCollection(file1, file2)
        TestFileCollection collection2 = new TestFileCollection(file2, file3)

        when:
        FileCollection sum = collection1.plus(collection2)

        then:
        sum instanceof UnionFileCollection
        sum.getFiles() == toLinkedSet(file1, file2, file3)
    }

    def "can add list of collections using + operator"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        TestFileCollection collection1 = new TestFileCollection(file1, file2)
        TestFileCollection collection2 = new TestFileCollection(file2, file3)

        when:
        FileCollection sum = collection1 + collection2

        then:
        sum instanceof UnionFileCollection
        sum.getFiles() == toLinkedSet(file1, file2, file3)
    }

    def "can subtract a collection"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        TestFileCollection collection1 = new TestFileCollection(file1, file2)
        TestFileCollection collection2 = new TestFileCollection(file2, file3)

        when:
        FileCollection difference = collection1.minus(collection2)

        then:
        difference.files == toLinkedSet(file1)
    }

    def "can subtract a collections using - operator"() {
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        TestFileCollection collection1 = new TestFileCollection(file1, file2)
        TestFileCollection collection2 = new TestFileCollection(file2, file3)

        when:
        FileCollection difference = collection1 - collection2

        then:
        difference.files == toLinkedSet(file1)
    }

    def "can visit the result of subtracting a collection"() {
        def visitor = Mock(FileCollectionStructureVisitor)
        File file1 = new File("f1")
        File file2 = new File("f2")
        File file3 = new File("f3")
        def collection1 = new TestFileCollection(file1, file2)
        def collection2 = new TestFileCollection(file2, file3)
        def difference = collection1.minus(collection2)

        when:
        difference.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, difference) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, difference)
        0 * visitor._
    }

    void canConvertToCollectionTypes() {
        File file = new File("f1")
        TestFileCollection collection = new TestFileCollection(file)

        expect:
        collection as Collection == toList(file)
        collection as Set == toLinkedSet(file)
        collection as List == toList(file)
    }

    void toFileTreeReturnsSingletonTreeForEachFileInCollection() {
        File file = testDir.createFile("f1")
        File file2 = testDir.createFile("f2")

        TestFileCollection collection = new TestFileCollection(file, file2)
        FileTree tree = collection.getAsFileTree()

        expect:
        FileVisitorUtil.assertVisits(tree, GUtil.map("f1", file, "f2", file2))
    }

    void canFilterContentsOfCollectionUsingSpec() {
        File file1 = new File("f1")
        File file2 = new File("f2")

        TestFileCollection collection = new TestFileCollection(file1, file2)
        FileCollection filtered = collection.filter(new Spec<File>() {
            boolean isSatisfiedBy(File element) {
                return element.getName().equals("f1")
            }
        })

        expect:
        assertThat(filtered.getFiles(), equalTo(toSet(file1)))
    }

    void canFilterContentsOfCollectionUsingClosure() {
        def file1 = new File("f1")
        def file2 = new File("f2")

        def collection = new TestFileCollection(file1, file2)
        def filtered = collection.filter { f -> f.name == 'f1' }

        expect:
        filtered.files == toSet(file1)
    }

    void filteredCollectionIsLive() {
        def file1 = new File("f1")
        def file2 = new File("f2")
        def file3 = new File("dir/f1")
        def collection = new TestFileCollection(file1, file2)

        when:
        def filtered = collection.filter { f -> f.name == 'f1' }

        then:
        filtered.files == toSet(file1)

        when:
        collection.files.add(file3)

        then:
        filtered.files == toSet(file1, file3)
    }

    void "can visit filtered collection"() {
        def file1 = new File("f1")
        def file2 = new File("f2")
        def collection = new TestFileCollection(file1, file2)
        def filtered = collection.filter { f -> f.name == 'f1' }
        def visitor = Mock(FileCollectionStructureVisitor)

        when:
        filtered.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, filtered) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, filtered)
        0 * _
    }

    void hasNoDependencies() {
        expect:
        assertThat(new TestFileCollection().getBuildDependencies().getDependencies(null), isEmpty())
    }

    void fileTreeHasSameDependenciesAsThis() {
        TestFileCollectionWithDependency collection = new TestFileCollectionWithDependency(dependency)
        collection.files.add(new File("f1"))

        expect:
        assertHasSameDependencies(collection.getAsFileTree())
        assertHasSameDependencies(collection.getAsFileTree().matching(TestUtil.TEST_CLOSURE))
    }

    void filteredCollectionHasSameDependenciesAsThis() {
        TestFileCollectionWithDependency collection = new TestFileCollectionWithDependency(dependency)

        expect:
        assertHasSameDependencies(collection.filter(TestUtil.toClosure("{true}")))
    }

    void elementsProviderHasNoDependenciesWhenThisHasNoDependencies() {
        def collection = new TestFileCollection()
        def action = Mock(Action)
        def elements = collection.elements

        when:
        def producer = elements.producer
        producer.visitProducerTasks(action)

        then:
        producer.known
        0 * action._

        expect:
        !elements.calculateExecutionTimeValue().hasChangingContent()
    }

    void elementsProviderHasSameDependenciesAsThis() {
        def collection = new TestFileCollectionWithDependency(dependency)
        def action = Mock(Action)
        def task = Mock(TaskInternal)
        _ * dependency.visitDependencies(_) >> { TaskDependencyResolveContext c -> c.add(task) }

        def elements = collection.elements

        when:
        def producer = elements.producer
        producer.visitProducerTasks(action)

        then:
        producer.known
        1 * action.execute(task)
        0 * action._

        expect:
        elements.calculateExecutionTimeValue().hasChangingContent()
    }

    void "visits self when listener requests contents"() {
        def collection = new TestFileCollection()
        def visitor = Mock(FileCollectionStructureVisitor)

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, collection) >> true
        1 * visitor.visitCollection(FileCollectionInternal.OTHER, collection)
        0 * visitor._
    }

    void "does not visit self when listener does not want contents"() {
        def collection = new TestFileCollection()
        def visitor = Mock(FileCollectionStructureVisitor)

        when:
        collection.visitStructure(visitor)

        then:
        1 * visitor.startVisit(FileCollectionInternal.OTHER, collection) >> false
        0 * visitor._
    }

    private void assertHasSameDependencies(FileCollection tree) {
        final Task task = Mock(Task.class)
        final Task depTask = Mock(Task.class)
        1 * dependency.visitDependencies(_) >> { TaskDependencyResolveContext c -> c.add(depTask) }
        0 * dependency._

        assertThat(tree.getBuildDependencies().getDependencies(task), equalTo((Object) toSet(depTask)))
    }

    static class TestFileCollection extends AbstractFileCollection {
        Set<File> files = new LinkedHashSet<File>()

        TestFileCollection(File... files) {
            this.files.addAll(Arrays.asList(files))
        }

        @Override
        String getDisplayName() {
            return "collection-display-name"
        }

        @Override
        Set<File> getFiles() {
            return files
        }
    }

    private class TestFileCollectionWithDependency extends TestFileCollection {
        private final TaskDependencyInternal dependency

        TestFileCollectionWithDependency(TaskDependencyInternal dependency, File... files) {
            super(files)
            this.dependency = dependency
        }

        @Override
        void visitDependencies(TaskDependencyResolveContext context) {
            context.add(dependency)
        }
    }
}
