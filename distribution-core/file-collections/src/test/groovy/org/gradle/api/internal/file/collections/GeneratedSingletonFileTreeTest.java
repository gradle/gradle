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
import org.gradle.internal.Factory;
import org.gradle.internal.MutableReference;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.gradle.api.file.FileVisitorUtil.assertVisits;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeneratedSingletonFileTreeTest {

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    private TestFile rootDir = tmpDir.getTestDirectory();

    private Factory<File> fileFactory = new Factory<File>() {
        public File create() {
            return rootDir;
        }
    };

    @Before
    public void setup() {
        NativeServicesTestFixture.initialize();
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
        GeneratedSingletonFileTree tree = tree("file.txt", fileAction);

        FileTreeAdapter fileTreeAdapter = new FileTreeAdapter(tree, TestFiles.getPatternSetFactory());
        File file = rootDir.file("file.txt");

        assertTrue(fileTreeAdapter.contains(file));
        assertTrue(fileTreeAdapter.contains(file));
        assertFalse(fileTreeAdapter.contains(rootDir.file("file2.txt")));

        assertEquals(0, callCounter.get());
    }

    @Test
    public void doesNotOverwriteFileWhenGeneratedContentRemainsTheSame() {
        Action<OutputStream> action = getAction();
        MinimalFileTree tree = tree("file.txt", action);

        assertVisits(tree, toList("file.txt"), Collections.<String>emptyList());

        TestFile file = rootDir.file("file.txt");

        file.assertContents(equalTo("content"));
        file.makeOlder();
        TestFile.Snapshot snapshot = file.snapshot();

        assertVisits(tree, toList("file.txt"), Collections.<String>emptyList());
        file.assertContents(equalTo("content"));
        file.assertHasNotChangedSince(snapshot);
    }

    @Test
    public void overwritesFileWhenGeneratedContentChanges() {
        final MutableReference<String> currentContentReference = MutableReference.of("content");
        GeneratedSingletonFileTree tree = tree("file.txt", new Action<OutputStream>() {
            @Override
            public void execute(OutputStream outputStream) {
                try {
                    outputStream.write(currentContentReference.get().getBytes());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        assertVisits(tree, toList("file.txt"), Collections.<String>emptyList());

        TestFile file = rootDir.file("file.txt");

        file.assertContents(equalTo("content"));
        TestFile.Snapshot snapshot = file.snapshot();

        currentContentReference.set("updated content");

        assertVisits(tree, toList("file.txt"), Collections.<String>emptyList());
        file.assertContents(equalTo("updated content"));
        file.assertHasChangedSince(snapshot);
    }

    private GeneratedSingletonFileTree tree(String fileName, Action<OutputStream> action) {
        return new GeneratedSingletonFileTree(fileFactory, fileName, it -> {}, action, TestFiles.fileSystem());
    }

    private Action<OutputStream> getAction() {
        return outputStream -> {
            try {
                outputStream.write("content".getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
