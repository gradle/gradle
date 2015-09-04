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
package org.gradle.api.internal.file.collections;

import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting;
import static org.gradle.api.file.FileVisitorUtil.assertVisits;
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

public class MapFileTreeTest {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private TestFile rootDir = tmpDir.getTestDirectory();
    private final MapFileTree tree = new MapFileTree(rootDir, TestFiles.fileSystem());

    @Test
    public void isEmptyByDefault() {
        List<String> emptyList = toList();
        assertVisits(tree, emptyList, emptyList);
        assertSetContainsForAllTypes(tree, emptyList);
    }

    @Test
    public void canAddAnElementUsingAClosureToGeneratedContent() {
        Action<OutputStream> action = getAction();
        tree.add("path/file.txt", action);

        assertVisits(tree, toList("path/file.txt"), toList("path"));
        assertSetContainsForAllTypes(tree, toList("path/file.txt"));

        rootDir.file("path").assertIsDir();
        rootDir.file("path/file.txt").assertContents(equalTo("content"));
    }

    @Test
    public void canAddMultipleElementsInDifferentDirs() {
        Action<OutputStream> action = getAction();
        tree.add("path/file.txt", action);
        tree.add("file.txt", action);
        tree.add("path/subdir/file.txt", action);

        assertVisits(tree, toList("path/file.txt", "file.txt", "path/subdir/file.txt"), toList("path", "path/subdir"));
        assertSetContainsForAllTypes(tree, toList("path/file.txt", "file.txt", "path/subdir/file.txt"));
    }

    @Test
    public void canStopVisitingElements() {
        Action<OutputStream> closure = getAction();
        tree.add("path/file.txt", closure);
        tree.add("file.txt", closure);
        assertCanStopVisiting(tree);
    }

    @Test
    public void containsWontCreateFiles() {
        final AtomicInteger callCounter = new AtomicInteger(0);
        Action<OutputStream> fileAction = new Action<OutputStream>() {
            @Override
            public void execute(OutputStream outputStream) {
                callCounter.incrementAndGet();
            }
        };
        tree.add("file.txt", fileAction);

        FileTreeAdapter fileTreeAdapter = new FileTreeAdapter(tree);
        File file = rootDir.file("file.txt");

        assertTrue(fileTreeAdapter.contains(file));
        assertTrue(fileTreeAdapter.contains(file));
        assertFalse(fileTreeAdapter.contains(rootDir.file("file2.txt")));

        assertEquals(0, callCounter.get());
    }

    private Action<OutputStream> getAction() {
        return new Action<OutputStream>() {
            public void execute(OutputStream outputStream) {
                try {
                    outputStream.write("content".getBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
