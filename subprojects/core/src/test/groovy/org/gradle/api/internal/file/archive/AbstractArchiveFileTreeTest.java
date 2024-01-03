/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import org.apache.commons.io.FileUtils;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.internal.Resources;
import org.junit.Rule;
import org.junit.Test;
import spock.lang.Issue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting;
import static org.gradle.api.file.FileVisitorUtil.assertVisits;
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes;
import static org.gradle.util.internal.WrapUtil.toList;
import static org.junit.Assert.assertEquals;

/**
 * Abstract base class for all tests of {@link AbstractArchiveFileTree} implementations which tests common
 * functionality.
 */
public abstract class AbstractArchiveFileTreeTest {
    @Rule public final TestNameTestDirectoryProvider tempDirProvider = new TestNameTestDirectoryProvider(getClass());
    @Rule public final Resources resources = new Resources(tempDirProvider);
    protected final TestFile tmpDir = tempDirProvider.getTestDirectory().file("tmp");
    protected final TestFile rootDir = tempDirProvider.getTestDirectory().file("root");

    protected abstract TestFile getArchiveFile();
    protected abstract AbstractArchiveFileTree getTree();
    protected abstract void archiveFileToRoot(TestFile file);

    @Issue("https://github.com/gradle/gradle/issues/22685")
    @Test
    public void testConcurrentArchiveVisiting() throws InterruptedException {
        Long numLines = 1000000L;
        int numFiles = 2;
        int numThreads = 3;

        // This visitor counts the lines in each file it visits, and makes available a list of all the line counts
        class CountingVisitor implements FileVisitor {
            private final List<Long> actualCounts = new ArrayList<>();

            public List<Long> getActualCounts() {
                return actualCounts;
            }

            @Override
            public void visitDir(FileVisitDetails dirDetails) { }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                try {
                    List<String> lines = FileUtils.readLines(fileDetails.getFile(), Charset.defaultCharset());
                    actualCounts.add((long) lines.size());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        // Generate a sufficiently large zip file containing some large files with random numbers, one per line
        Random random = new Random();
        for (int i = 0; i < numFiles; i++) {
            StringBuilder sb = new StringBuilder();
            for (long j = 0; j < numLines; j++) {
                sb.append(random.nextInt()).append('\n');
            }
            rootDir.file("file" + i + ".txt").write(sb.toString());
        }
        archiveFileToRoot(getArchiveFile());

        // Create some visitors that will count the number of lines in each file they encounter
        List<CountingVisitor> visitors = new ArrayList<>(numThreads);
        for (int i = 0; i < numThreads; i++) {
            visitors.add(new CountingVisitor());
        }

        // Create callables that will send the visitors to visit the archive
        List<Callable<List<Long>>> callables = visitors.stream().map(v -> {
                return new Callable<List<Long>>() {
                    @Override
                    public List<Long> call() {
                        getTree().visit(v);
                        return v.getActualCounts();
                    }
                };
        }).collect(Collectors.toList());

        // Concurrently visit the archive
        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        List<Future<List<Long>>> results = executorService.invokeAll(callables);

        // And check that each visitor counted the complete number of lines in each file in the archive
        // (i.e. that the archive was not visited by the second visitor before the first visitor had finished fully decompressing it)
        results.forEach(f -> {
            try {
                f.get().forEach(result -> assertEquals("Files should only be read after full expansion when all lines are present", numLines, result));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void doesNotOverwriteFilesOnSecondVisit() {
        TestFile contentFile = rootDir.file("file1.txt");
        contentFile.write("content");
        archiveFileToRoot(getArchiveFile());

        assertVisits(getTree(), toList("file1.txt"), new ArrayList<>());
        contentFile.makeOlder();
        TestFile.Snapshot snapshot = contentFile.snapshot();
        assertVisits(getTree(), toList("file1.txt"), new ArrayList<>());
        contentFile.assertHasNotChangedSince(snapshot);
    }

    @Test
    public void visitsContentsOfArchiveFile() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        archiveFileToRoot(getArchiveFile());

        assertVisits(getTree(), toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(getTree(), toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void canStopVisitingFiles() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir/other/file2.txt").write("content");
        archiveFileToRoot(getArchiveFile());

        assertCanStopVisiting(getTree());
    }
}
