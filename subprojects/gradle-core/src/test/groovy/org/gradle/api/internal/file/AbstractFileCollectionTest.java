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
package org.gradle.api.internal.file;

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import static org.gradle.api.tasks.AntBuilderAwareUtil.*;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskDependency;
import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.*;
import org.gradle.util.HelperUtil;
import org.gradle.integtests.TestFile;
import org.hamcrest.Matcher;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.*;

public class AbstractFileCollectionTest {
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
            assertThat(e.getMessage(), equalTo(
                    "Expected collection-display-name to contain exactly one file, however, it contains 2 files."));
        }
    }

    @Test
    public void failsToGetSingleFileWhenCollectionIsEmpty() {
        TestFileCollection collection = new TestFileCollection();
        try {
            collection.getSingleFile();
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo(
                    "Expected collection-display-name to contain exactly one file, however, it contains no files."));
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
    public void cannotAddCollection() {
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
        TestFile testDir = HelperUtil.makeNewTestDir();
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
    public void throwsStopExceptionWhenEmpy() {
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
    public void toFileTreeReturnsSingletonTreeForEachFile() {
        File file = new File("f1");

        TestFileCollection collection = new TestFileCollection(file);
        FileTree tree = collection.getAsFileTree();
        assertThat(tree, instanceOf(CompositeFileTree.class));
        CompositeFileTree compositeTree = (CompositeFileTree) tree;
        assertThat(compositeTree.getSourceCollections(), hasItems((Matcher) instanceOf(SingletonFileTree.class)));
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
    public void hasNoDependencies() {
        assertThat(new TestFileCollection().getBuildDependencies().getDependencies(null), isEmpty());
    }

    @Test
    public void fileTreeHasSameDependenciesAsThis() {
        final TaskDependency dependency = new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                throw new UnsupportedOperationException();
            }
        };
        TestFileCollection collection = new TestFileCollection() {
            @Override
            public TaskDependency getBuildDependencies() {
                return dependency;
            }
        };
        collection.files.add(new File("f1"));

        assertThat(collection.getAsFileTree().getBuildDependencies(), sameInstance(dependency));
    }

    private class TestFileCollection extends AbstractFileCollection {
        private Set<File> files = new LinkedHashSet<File>();

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
}
