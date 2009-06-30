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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.FileCollection;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public class AbstractFileCollectionTest {
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
    public void canGetFilesAsAPath() {
        File file1 = new File("f1");
        File file2 = new File("f2");

        TestFileCollection collection = new TestFileCollection(file1, file2);
        assertThat(collection.getAsPath(), equalTo(file1 + File.pathSeparator + file2));
    }

    @Test
    public void canAddCollections() {
        File file1 = new File("f1");
        File file2 = new File("f2");
        File file3 = new File("f3");

        TestFileCollection collection1 = new TestFileCollection(file1, file2);
        TestFileCollection collection2 = new TestFileCollection(file2, file3);
        FileCollection sum = collection1.plus(collection2);
        assertThat(sum, instanceOf(UnionFileCollection.class));
        assertThat(sum.getFiles(), equalTo(WrapUtil.toLinkedSet(file1, file2, file3)));
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
