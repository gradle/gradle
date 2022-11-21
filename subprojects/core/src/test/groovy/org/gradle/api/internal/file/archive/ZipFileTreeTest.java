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
package org.gradle.api.internal.file.archive;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.TestUtil;
import org.gradle.util.internal.Resources;
import org.junit.Rule;
import org.junit.Test;
import spock.lang.Issue;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting;
import static org.gradle.api.file.FileVisitorUtil.assertVisits;
import static org.gradle.api.file.FileVisitorUtil.assertVisitsPermissions;
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory;
import static org.gradle.api.internal.file.TestFiles.fileHasher;
import static org.gradle.api.internal.file.TestFiles.fileSystem;
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes;
import static org.gradle.util.internal.WrapUtil.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ZipFileTreeTest {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    @Rule public final Resources resources = new Resources();
    private final TestFile zipFile = tmpDir.getTestDirectory().file("test.zip");
    private final TestFile rootDir = tmpDir.getTestDirectory().file("root");
    private final TestFile expandDir = tmpDir.getTestDirectory().file("tmp");
    private final ZipFileTree tree = new ZipFileTree(TestUtil.providerFactory().provider(()->zipFile), expandDir, fileSystem(), directoryFileTreeFactory(), fileHasher());

    @Test
    public void displayName() {
        assertThat(tree.getDisplayName(), equalTo("ZIP '" + zipFile + "'"));
    }

    @Test
    public void visitsContentsOfZipFile() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        rootDir.zipTo(zipFile);

        assertVisits(tree, toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(tree, toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void canStopVisitingFiles() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir/other/file2.txt").write("content");
        rootDir.zipTo(zipFile);

        assertCanStopVisiting(tree);
    }

    @Test
    public void failsWhenZipFileDoesNotExist() {
        try {
            tree.visit(new EmptyFileVisitor());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + zipFile + "' as it does not exist."));
        }
    }

    @Test
    public void failsWhenZipFileIsADirectory() {
        zipFile.createDir();

        try {
            tree.visit(new EmptyFileVisitor());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + zipFile + "' as it is not a file."));
        }
    }

    @Test
    public void wrapsFailureToUnzipFile() {
        zipFile.write("not a zip file");

        try {
            tree.visit(new EmptyFileVisitor());
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + zipFile + "'."));
        }
    }

    @Test
    public void expectedFilePermissionsAreFound() {
        resources.findResource("permissions.zip").copyTo(zipFile);

        final Map<String, Integer> expected = new HashMap<>();
        expected.put("file", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void expectedDefaultForNoModeZips() {
        resources.findResource("nomodeinfos.zip").copyTo(zipFile);

        final Map<String, Integer> expected = new HashMap<>();
        expected.put("file.txt", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void doesNotOverwriteFilesOnSecondVisit() {
        rootDir.file("file1.txt").write("content");
        rootDir.zipTo(zipFile);

        assertVisits(tree, toList("file1.txt"), new ArrayList<>());
        TestFile content = expandDir.listFiles()[0].listFiles()[0];
        content.makeOlder();
        TestFile.Snapshot snapshot = content.snapshot();
        assertVisits(tree, toList("file1.txt"), new ArrayList<>());
        content.assertHasNotChangedSince(snapshot);
    }

    @Issue("https://github.com/gradle/gradle/issues/22685")
    @Test
    public void testZipVisiting() throws InterruptedException {
        Long numLines = 1000000L;
        int numFiles = 2;
        int numThreads = 3;

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
        rootDir.zipTo(zipFile);

        // Create some visitors that will count the number of lines in each file they encounter
        List<CountingVisitor> visitors = new ArrayList<>(numThreads);
        for (int i = 0; i < numThreads; i++) {
            visitors.add(new CountingVisitor());
        }

        List<Callable<List<Long>>> callables = visitors.stream().map(v -> (Callable<List<Long>>) () -> {
            tree.visit(v);
            return v.getActualCounts();
        }).collect(Collectors.toList());

        ExecutorService executorService = Executors.newFixedThreadPool(numThreads);
        List<Future<List<Long>>> results = executorService.invokeAll(callables);

        results.forEach(f -> {
            try {
                f.get().forEach(result -> assertEquals("Files should only be read after full expansion when all lines are present", numLines, result));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
