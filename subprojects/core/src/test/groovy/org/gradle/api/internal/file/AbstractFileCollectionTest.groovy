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
package org.gradle.api.internal.file;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitorUtil;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.GUtil;
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices;
import org.junit.Rule;
import spock.lang.Specification;

import static org.gradle.api.tasks.AntBuilderAwareUtil.*;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@UsesNativeServices
public class AbstractFileCollectionTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    final TaskDependency dependency = Mock(TaskDependency.class);

    public void usesDisplayNameAsToString() {
        TestFileCollection collection = new TestFileCollection();

        expect:
        assertThat(collection.toString(), equalTo("collection-display-name"));
    }

    public void canIterateOverFiles() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        TestFileCollection collection = new TestFileCollection(file1, file2);

        expect:
        Iterator<File> iterator = collection.iterator();
        assertThat(iterator.next(), sameInstance(file1));
        assertThat(iterator.next(), sameInstance(file2));
        assertFalse(iterator.hasNext());
    }

    public void canGetSingleFile() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        expect:
        assertThat(collection.getSingleFile(), sameInstance(file));
    }

    public void failsToGetSingleFileWhenCollectionContainsMultipleFiles() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        TestFileCollection collection = new TestFileCollection(file1, file2);

        expect:
        try {
            collection.getSingleFile();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Expected collection-display-name to contain exactly one file, however, it contains 2 files."));
        }
    }

    public void failsToGetSingleFileWhenCollectionIsEmpty() {
        TestFileCollection collection = new TestFileCollection();

        expect:
        try {
            collection.getSingleFile();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Expected collection-display-name to contain exactly one file, however, it contains no files."));
        }
    }

    public void containsFile() {
        File file1 = new File("f1");
        TestFileCollection collection = new TestFileCollection(file1);

        expect:
        assertTrue(collection.contains(file1));
        assertFalse(collection.contains(new File("f2")));
    }

    public void canGetFilesAsAPath() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        TestFileCollection collection = new TestFileCollection(file1, file2);

        expect:
        assertThat(collection.getAsPath(), equalTo(file1.path + File.pathSeparator + file2.path));
    }

    public void canAddCollectionsTogether() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection sum = collection1.plus(collection2);

        then:
        assertThat(sum, instanceOf(UnionFileCollection.class));
        assertThat(sum.getFiles(), equalTo(toLinkedSet(file1, file2, file3)));
    }

    def "can add collections using + operator"() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection sum = collection1 + collection2

        then:
        sum instanceof UnionFileCollection
        sum.getFiles() == toLinkedSet(file1, file2, file3)
    }

    def "can add a list of collections"() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection sum = collection1.plus(collection2)

        then:
        sum instanceof UnionFileCollection
        sum.getFiles() == toLinkedSet(file1, file2, file3)
    }

    def "can add list of collections using + operator"() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection sum = collection1 + collection2

        then:
        sum instanceof UnionFileCollection
        sum.getFiles() == toLinkedSet(file1, file2, file3)
    }

    public void canSubtractCollections() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection difference = collection1.minus(collection2);

        then:
        assertThat(difference.getFiles(), equalTo(toLinkedSet(file1)));
    }

    def "can subtract a collection using - operator"() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection difference = collection1 - collection2

        then:
        difference.files == toLinkedSet(file1)
    }

    def "can subtract a list of collection"() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection difference = collection1.minus(collection2)

        then:
        difference.files == toLinkedSet(file1)
    }

    def "can subtract a list of collections using - operator"() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");
        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);

        when:
        FileCollection difference = collection1 - collection2

        then:
        difference.files == toLinkedSet(file1)
    }

    public void cannotAddCollectionToThisCollection() {
        expect:
        try {
            new TestFileCollection().add(new TestFileCollection());
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), equalTo("Collection-display-name does not allow modification."));
        }
    }

    public void canAddToAntBuilderAsResourceCollection() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        TestFileCollection collection = new TestFileCollection(file1, file2);

        expect:
        assertSetContains(collection, toSet("f1", "f2"));
    }

    public void includesOnlyExistingFilesWhenAddedToAntBuilderAsAFileSetOrMatchingTask() {
        TestFile testDir = this.testDir.getTestDirectory();
        TestFile file1 = testDir.file("f1").touch();
        TestFile dir1 = testDir.file("dir1").createDir();
        TestFile file2 = dir1.file("f2").touch();
        TestFile missing = testDir.file("f3");
        testDir.file("f2").touch();
        testDir.file("ignored1").touch();
        dir1.file("f1").touch();
        dir1.file("ignored1").touch();
        TestFileCollection collection = new TestFileCollection(file1, file2, dir1, missing);

        expect:
        assertSetContainsForFileSet(collection, toSet("f1", "f2"));
        assertSetContainsForMatchingTask(collection, toSet("f1", "f2"));
    }

    public void isEmptyWhenFilesIsEmpty() {
        expect:
        assertTrue(new TestFileCollection().isEmpty());
        assertFalse(new TestFileCollection(new File("f1")).isEmpty());
    }

    public void throwsStopExceptionWhenEmpty() {
        TestFileCollection collection = new TestFileCollection();

        expect:
        try {
            collection.stopExecutionIfEmpty();
            fail();
        } catch (StopExecutionException e) {
            assertThat(e.getMessage(), equalTo("Collection-display-name does not contain any files."));
        }
    }

    public void doesNotThrowStopExceptionWhenNotEmpty() {
        TestFileCollection collection = new TestFileCollection(new File("f1"));

        when:
        collection.stopExecutionIfEmpty();

        then:
        noExceptionThrown()
    }

    public void canConvertToCollectionTypes() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        expect:
        assertThat(collection.asType(Collection.class), equalTo((Object) toLinkedSet(file)));
        assertThat(collection.asType(Set.class), equalTo((Object) toLinkedSet(file)));
        assertThat(collection.asType(List.class), equalTo((Object) toList(file)));
    }

    public void canConvertToArray() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        expect:
        assertThat(collection.asType(File[].class), equalTo((Object) toArray(file)));
    }

    public void canConvertCollectionWithSingleFileToFile() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        expect:
        assertThat(collection.asType(File.class), equalTo((Object) file));
    }

    public void canConvertToFileTree() {
        TestFileCollection collection = new TestFileCollection();

        expect:
        assertThat(collection.asType(FileTree.class), notNullValue());
    }

    public void throwsUnsupportedOperationExceptionWhenConvertingToUnsupportedType() {
        expect:
        try {
            new TestFileCollection().asType(Integer.class);
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), equalTo("Cannot convert collection-display-name to type Integer, as this type is not supported."));
        }
    }

    public void toFileTreeReturnsSingletonTreeForEachFileInCollection() {
        File file = testDir.createFile("f1");
        File file2 = testDir.createFile("f2");

        TestFileCollection collection = new TestFileCollection(file, file2);
        FileTree tree = collection.getAsFileTree();

        expect:
        FileVisitorUtil.assertVisits(tree, GUtil.map("f1", file, "f2", file2));
    }

    public void canFilterContentsOfCollectionUsingSpec() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        FileCollection filtered = collection.filter(new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return element.getName().equals("f1");
            }
        });

        expect:
        assertThat(filtered.getFiles(), equalTo(toSet(file1)));
    }

    public void canFilterContentsOfCollectionUsingClosure() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        FileCollection filtered = collection.filter(TestUtil.toClosure("{f -> f.name == 'f1'}"));

        expect:
        assertThat(filtered.getFiles(), equalTo(toSet(file1)));
    }

    public void filteredCollectionIsLive() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("dir/f1");
        TestFileCollection collection = new TestFileCollection(file1, file2);

        when:
        FileCollection filtered = collection.filter(TestUtil.toClosure("{f -> f.name == 'f1'}"));

        then:
        assertThat(filtered.getFiles(), equalTo(toSet(file1)));

        when:
        collection.files.add(file3);

        then:
        assertThat(filtered.getFiles(), equalTo(toSet(file1, file3)));
    }

    public void hasNoDependencies() {
        expect:
        assertThat(new TestFileCollection().getBuildDependencies().getDependencies(null), isEmpty());
    }

    public void fileTreeHasSameDependenciesAsThis() {
        TestFileCollectionWithDependency collection = new TestFileCollectionWithDependency();
        collection.files.add(new File("f1"));

        expect:
        assertHasSameDependencies(collection.getAsFileTree());
        assertHasSameDependencies(collection.getAsFileTree().matching(TestUtil.TEST_CLOSURE));
    }

    public void filteredCollectionHasSameDependenciesAsThis() {
        TestFileCollectionWithDependency collection = new TestFileCollectionWithDependency();

        expect:
        assertHasSameDependencies(collection.filter(TestUtil.toClosure("{true}")));
    }

    public void canVisitRootElements() {
        def collection = new TestFileCollection()
        def visitor = Mock(FileCollectionVisitor)

        when:
        collection.visitRootElements(visitor)

        then:
        1 * visitor.visitCollection(collection)
        0 * visitor._
    }

    private void assertHasSameDependencies(FileCollection tree) {
        final Task task = Mock(Task.class);
        final Task depTask = Mock(Task.class);
        1 * dependency.getDependencies(task) >> { [ depTask ] }

        assertThat(tree.getBuildDependencies().getDependencies(task), equalTo((Object) toSet(depTask)));
    }

    private class TestFileCollection extends AbstractFileCollection {
        Set<File> files = new LinkedHashSet<File>();

        TestFileCollection(File... files) {
            this.files.addAll(Arrays.asList(files));
        }

        public String getDisplayName() {
            return "collection-display-name";
        }

        public Set<File> getFiles() {
            return files;
        }
    }

    private class TestFileCollectionWithDependency extends TestFileCollection {
        TestFileCollectionWithDependency(File... files) {
            super(files)
        }

        @Override
        public TaskDependency getBuildDependencies() {
            return dependency;
        }
    }
}
