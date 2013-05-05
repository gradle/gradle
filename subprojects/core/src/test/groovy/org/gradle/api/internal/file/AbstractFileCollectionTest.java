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
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

import static org.gradle.api.tasks.AntBuilderAwareUtil.*;
import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class AbstractFileCollectionTest {
    @Rule
    public final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    final JUnit4Mockery context = new JUnit4GroovyMockery();
    final TaskDependency dependency = context.mock(TaskDependency.class);

    @Test
    public void usesDisplayNameAsToString() {
        TestFileCollection collection = new TestFileCollection();
        assertThat(collection.toString(), equalTo("collection-display-name"));
    }

    @Test
    public void canIterateOverFiles() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        Iterator<File> iterator = collection.iterator();
        assertThat(iterator.next(), sameInstance(file1));
        assertThat(iterator.next(), sameInstance(file2));
        assertFalse(iterator.hasNext());
    }

    @Test
    public void canGetSingleFile() {
        File file = new File("f1");

        TestFileCollection collection = new TestFileCollection(file);
        assertThat(collection.getSingleFile(), sameInstance(file));
    }

    @Test
    public void failsToGetSingleFileWhenCollectionContainsMultipleFiles() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        try {
            collection.getSingleFile();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Expected collection-display-name to contain exactly one file, however, it contains 2 files."));
        }
    }

    @Test
    public void failsToGetSingleFileWhenCollectionIsEmpty() {
        TestFileCollection collection = new TestFileCollection();
        try {
            collection.getSingleFile();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Expected collection-display-name to contain exactly one file, however, it contains no files."));
        }
    }

    @Test
    public void containsFile() {
        File file1 = new File("f1");

        TestFileCollection collection = new TestFileCollection(file1);
        assertTrue(collection.contains(file1));
        assertFalse(collection.contains(new File("f2")));
    }

    @Test
    public void canGetFilesAsAPath() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        assertThat(collection.getAsPath(), equalTo(file1 + File.pathSeparator + file2));
    }

    @Test
    public void canAddCollectionsTogether() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");

        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);
        FileCollection sum = collection1.plus(collection2);
        assertThat(sum, instanceOf(UnionFileCollection.class));
        assertThat(sum.getFiles(), equalTo(toLinkedSet(file1, file2, file3)));
    }

    @Test
    public void canSubtractCollections() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");

        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);
        FileCollection sum = collection1.minus(collection2);
        assertThat(sum.getFiles(), equalTo(toLinkedSet(file1)));
    }

    @Test
    public void cannotAddCollectionToThisCollection() {
        try {
            new TestFileCollection().add(new TestFileCollection());
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), equalTo("Collection-display-name does not allow modification."));
        }
    }

    @Test
    public void canAddToAntBuilderAsResourceCollection() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        assertSetContains(collection, toSet("f1", "f2"));
    }

    @Test
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
        assertSetContainsForFileSet(collection, toSet("f1", "f2"));
        assertSetContainsForMatchingTask(collection, toSet("f1", "f2"));
    }

    @Test
    public void isEmptyWhenFilesIsEmpty() {
        assertTrue(new TestFileCollection().isEmpty());
        assertFalse(new TestFileCollection(new File("f1")).isEmpty());
    }

    @Test
    public void throwsStopExceptionWhenEmpty() {
        TestFileCollection collection = new TestFileCollection();
        try {
            collection.stopExecutionIfEmpty();
            fail();
        } catch (StopExecutionException e) {
            assertThat(e.getMessage(), equalTo("Collection-display-name does not contain any files."));
        }
    }

    @Test
    public void doesNotThrowStopExceptionWhenNotEmpty() {
        TestFileCollection collection = new TestFileCollection(new File("f1"));
        collection.stopExecutionIfEmpty();
    }

    @Test
    public void canConvertToCollectionTypes() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        assertThat(collection.asType(Collection.class), equalTo((Object) toLinkedSet(file)));
        assertThat(collection.asType(Set.class), equalTo((Object) toLinkedSet(file)));
        assertThat(collection.asType(List.class), equalTo((Object) toList(file)));
    }

    @Test
    public void canConvertToArray() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        assertThat(collection.asType(File[].class), equalTo((Object) toArray(file)));
    }

    @Test
    public void canConvertCollectionWithSingleFileToFile() {
        File file = new File("f1");
        TestFileCollection collection = new TestFileCollection(file);

        assertThat(collection.asType(File.class), equalTo((Object) file));
    }

    @Test
    public void canConvertToFileTree() {
        TestFileCollection collection = new TestFileCollection();
        assertThat(collection.asType(FileTree.class), notNullValue());
    }

    @Test
    public void throwsUnsupportedOperationExceptionWhenConvertingToUnsupportedType() {
        try {
            new TestFileCollection().asType(Integer.class);
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), equalTo("Cannot convert collection-display-name to type Integer, as this type is not supported."));
        }
    }

    @Test
    public void toFileTreeReturnsSingletonTreeForEachFileInCollection() {
        File file = testDir.createFile("f1");
        File file2 = testDir.createFile("f2");

        TestFileCollection collection = new TestFileCollection(file, file2);
        FileTree tree = collection.getAsFileTree();
        FileVisitorUtil.assertVisits(tree, GUtil.map("f1", file, "f2", file2));
    }

    @Test
    public void canFilterContentsOfCollectionUsingSpec() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        FileCollection filtered = collection.filter(new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return element.getName().equals("f1");
            }
        });
        assertThat(filtered.getFiles(), equalTo(toSet(file1)));
    }

    @Test
    public void canFilterContentsOfCollectionUsingClosure() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        FileCollection filtered = collection.filter(HelperUtil.toClosure("{f -> f.name == 'f1'}"));
        assertThat(filtered.getFiles(), equalTo(toSet(file1)));
    }

    @Test
    public void filteredCollectionIsLive() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("dir/f1");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        FileCollection filtered = collection.filter(HelperUtil.toClosure("{f -> f.name == 'f1'}"));
        assertThat(filtered.getFiles(), equalTo(toSet(file1)));

        collection.files.add(file3);
        assertThat(filtered.getFiles(), equalTo(toSet(file1, file3)));
    }

    @Test
    public void hasNoDependencies() {
        assertThat(new TestFileCollection().getBuildDependencies().getDependencies(null), isEmpty());
    }

    @Test
    public void fileTreeHasSameDependenciesAsThis() {
        TestFileCollectionWithDependency collection = new TestFileCollectionWithDependency();
        collection.files.add(new File("f1"));

        assertHasSameDependencies(collection.getAsFileTree());
        assertHasSameDependencies(collection.getAsFileTree().matching(HelperUtil.TEST_CLOSURE));
    }

    @Test
    public void filteredCollectionHasSameDependenciesAsThis() {
        TestFileCollectionWithDependency collection = new TestFileCollectionWithDependency();

        assertHasSameDependencies(collection.filter(HelperUtil.toClosure("{true}")));
    }

    private void assertHasSameDependencies(FileCollection tree) {
        final Task task = context.mock(Task.class);
        final Task depTask = context.mock(Task.class);
        context.checking(new Expectations() {{
            one(dependency).getDependencies(task);
            will(returnValue(toSet(depTask)));
        }});

        assertThat(tree.getBuildDependencies().getDependencies(task), equalTo((Object) toSet(depTask)));
    }

    private class TestFileCollection extends AbstractFileCollection {
        Set<File> files = new LinkedHashSet<File>();

        private TestFileCollection(File... files) {
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
        @Override
        public TaskDependency getBuildDependencies() {
            return dependency;
        }
    }
}
