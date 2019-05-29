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

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.Resources;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.file.FileVisitorUtil.*;
import static org.gradle.api.internal.file.TestFiles.*;
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class ZipFileTreeTest {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    @Rule public final Resources resources = new Resources();
    private final TestFile zipFile = tmpDir.getTestDirectory().file("test.zip");
    private final TestFile rootDir = tmpDir.getTestDirectory().file("root");
    private final TestFile expandDir = tmpDir.getTestDirectory().file("tmp");
    private final ZipFileTree tree = new ZipFileTree(zipFile, expandDir, fileSystem(), directoryFileTreeFactory(), fileHasher());

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
            tree.visit(null);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + zipFile + "' as it does not exist."));
        }
    }

    @Test
    public void failsWhenZipFileIsADirectory() {
        zipFile.createDir();

        try {
            tree.visit(null);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + zipFile + "' as it is not a file."));
        }
    }

    @Test
    public void wrapsFailureToUnzipFile() {
        zipFile.write("not a zip file");

        try {
            tree.visit(null);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Could not expand ZIP '" + zipFile + "'."));
        }
    }

    @Test
    public void expectedFilePermissionsAreFound() {
        resources.findResource("permissions.zip").copyTo(zipFile);

        final Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put("file", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void expectedDefaultForNoModeZips() {
        resources.findResource("nomodeinfos.zip").copyTo(zipFile);

        final Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put("file.txt", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void doesNotOverwriteFilesOnSecondVisit() throws InterruptedException {
        rootDir.file("file1.txt").write("content");
        rootDir.zipTo(zipFile);

        assertVisits(tree, toList("file1.txt"), new ArrayList<String>());
        TestFile content = expandDir.listFiles()[0].listFiles()[0];
        content.makeOlder();
        TestFile.Snapshot snapshot = content.snapshot();
        assertVisits(tree, toList("file1.txt"), new ArrayList<String>());
        content.assertHasNotChangedSince(snapshot);
    }
}
