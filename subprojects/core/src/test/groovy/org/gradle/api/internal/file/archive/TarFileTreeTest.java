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
import org.gradle.api.internal.file.MaybeCompressedFileResource;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.internal.LocalResourceAdapter;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.Resources;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.file.FileVisitorUtil.assertCanStopVisiting;
import static org.gradle.api.file.FileVisitorUtil.assertVisits;
import static org.gradle.api.file.FileVisitorUtil.assertVisitsPermissions;
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory;
import static org.gradle.api.internal.file.TestFiles.fileHasher;
import static org.gradle.api.internal.file.TestFiles.fileSystem;
import static org.gradle.api.internal.file.TestFiles.streamHasher;
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class TarFileTreeTest {
    @Rule public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    @Rule public final Resources resources = new Resources();
    private final TestFile tarFile = tmpDir.getTestDirectory().file("test.tar");
    private final TestFile rootDir = tmpDir.getTestDirectory().file("root");
    private final TestFile expandDir = tmpDir.getTestDirectory().file("tmp");
    private final TarFileTree tree = new TarFileTree(tarFile, new MaybeCompressedFileResource(new LocalResourceAdapter(TestFiles.fileRepository().localResource(tarFile))), expandDir, fileSystem(), fileSystem(), directoryFileTreeFactory(), streamHasher(), fileHasher());

    @Test
    public void displayName() {
        assertThat(tree.getDisplayName(), equalTo("TAR '" + tarFile + "'"));
    }

    @Test
    public void visitsContentsOfTarFile() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        rootDir.tarTo(tarFile);

        assertVisits(tree, toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(tree, toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void readsGzippedTarFile() {
        TestFile tgz = tmpDir.getTestDirectory().file("test.tgz");

        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        rootDir.tgzTo(tgz);

        MaybeCompressedFileResource resource = new MaybeCompressedFileResource(new LocalResourceAdapter(TestFiles.fileRepository().localResource(tgz)));
        TarFileTree tree = new TarFileTree(tgz, resource, expandDir, fileSystem(), fileSystem(), directoryFileTreeFactory(), streamHasher(), fileHasher());

        assertVisits(tree, toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(tree, toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void readsBzippedTarFile() {
        TestFile tbz2 = tmpDir.getTestDirectory().file("test.tbz2");

        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        rootDir.tbzTo(tbz2);

        MaybeCompressedFileResource resource = new MaybeCompressedFileResource(new LocalResourceAdapter(TestFiles.fileRepository().localResource(tbz2)));
        TarFileTree tree = new TarFileTree(tbz2, resource, expandDir, fileSystem(), fileSystem(), directoryFileTreeFactory(), streamHasher(), fileHasher());

        assertVisits(tree, toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(tree, toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void canStopVisitingFiles() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir/other/file2.txt").write("content");
        rootDir.tarTo(tarFile);

        assertCanStopVisiting(tree);
    }

    @Test
    public void failsWhenTarFileDoesNotExist() {
        try {
            tree.visit(null);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), containsString("Cannot expand TAR '" + tarFile + "'."));
            assertThat(e.getCause(), instanceOf(MissingResourceException.class));
        }
    }

    @Test
    public void failsWhenTarFileIsADirectory() {
        tarFile.createDir();

        try {
            tree.visit(null);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), containsString("Cannot expand TAR '" + tarFile + "'"));
        }
    }

    @Test
    public void wrapsFailureToUntarFile() {
        expandDir.write("not a directory");
        tarFile.write("not a tar file");

        try {
            tree.visit(null);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), containsString("Unable to expand TAR '" + tarFile + "'"));
        }
    }

    @Test
    public void expectedFilePermissionsAreFound() {
        resources.findResource("permissions.tar").copyTo(tarFile);

        final Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put("file", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void readsTarFileWithNullPermissions() {
        resources.findResource("nullpermissions.tar").copyTo(tarFile);

        final Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put("bin", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void doesNotOverwriteFilesOnSecondVisit() throws InterruptedException {
        rootDir.file("file1.txt").write("content");
        rootDir.tarTo(tarFile);

        assertVisits(tree, toList("file1.txt"), new ArrayList<String>());
        TestFile content = expandDir.listFiles()[0].listFiles()[0];
        content.makeOlder();
        TestFile.Snapshot snapshot = content.snapshot();
        assertVisits(tree, toList("file1.txt"), new ArrayList<String>());
        content.assertHasNotChangedSince(snapshot);
    }
}
