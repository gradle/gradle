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
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.internal.file.MaybeCompressedFileResource;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.resources.internal.LocalResourceAdapter;
import org.gradle.cache.internal.TestCaches;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.file.FileVisitorUtil.assertVisits;
import static org.gradle.api.file.FileVisitorUtil.assertVisitsPermissions;
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory;
import static org.gradle.api.internal.file.TestFiles.fileHasher;
import static org.gradle.api.internal.file.TestFiles.fileSystem;
import static org.gradle.api.tasks.AntBuilderAwareUtil.assertSetContainsForAllTypes;
import static org.gradle.util.internal.WrapUtil.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

public class TarFileTreeTest extends AbstractArchiveFileTreeTest {
    private final TestFile archiveFile = tempDirProvider.getTestDirectory().file("test.tar");
    private final TarFileTree tree = tarTree(archiveFile);

    private TarFileTree tarTree(File archiveFile) {
        return new TarFileTree(
            Providers.of(archiveFile),
            Providers.of(new MaybeCompressedFileResource(new LocalResourceAdapter(TestFiles.fileRepository().localResource(archiveFile)))),
            fileSystem(),
            directoryFileTreeFactory(),
            fileHasher(),
            TestCaches.decompressionCache(tempDirProvider.getTestDirectory().createDir("cache-dir")),
            TestFiles.tmpDirTemporaryFileProvider(tempDirProvider.getTestDirectory()));
    }

    @Override
    protected void archiveFileToRoot(TestFile file) {
        rootDir.tarTo(file);
    }

    @Override
    protected TestFile getArchiveFile() {
        return archiveFile;
    }

    @Override
    protected TarFileTree getTree() {
        return tree;
    }

    @Test
    public void readsGzippedTarFile() {
        TestFile tgz = tempDirProvider.getTestDirectory().file("test.tgz");

        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        rootDir.tgzTo(tgz);

        TarFileTree tree = tarTree(tgz);

        assertVisits(tree, toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(tree, toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void readsBzippedTarFile() {
        TestFile tbz2 = tempDirProvider.getTestDirectory().file("test.tbz2");

        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir2/file2.txt").write("content");
        rootDir.tbzTo(tbz2);

        TarFileTree tree = tarTree(tbz2);

        assertVisits(tree, toList("subdir/file1.txt", "subdir2/file2.txt"), toList("subdir", "subdir2"));
        assertSetContainsForAllTypes(tree, toList("subdir/file1.txt", "subdir2/file2.txt"));
    }

    @Test
    public void failsWhenArchiveFileDoesNotExist() {
        try {
            tree.visit(new EmptyFileVisitor());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), containsString("Cannot expand TAR '" + archiveFile + "' as it does not exist."));
        }
    }

    @Test
    public void failsWhenArchiveFileIsADirectory() {
        archiveFile.createDir();

        try {
            tree.visit(new EmptyFileVisitor());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), containsString("Cannot expand TAR '" + archiveFile + "'"));
        }
    }

    @Test
    public void wrapsFailureToUnarchiveFile() {
        archiveFile.write("not a tar file");

        try {
            tree.visit(new EmptyFileVisitor());
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), containsString("Unable to expand TAR '" + archiveFile + "'"));
        }
    }


    @Test
    public void expectedFilePermissionsAreFound() {
        resources.findResource("permissions.tar").copyTo(archiveFile);

        final Map<String, Integer> expected = new HashMap<>();
        expected.put("file", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void readsTarFileWithNullPermissions() {
        resources.findResource("nullpermissions.tar").copyTo(archiveFile);

        final Map<String, Integer> expected = new HashMap<>();
        expected.put("bin", 0755);

        assertVisitsPermissions(tree, expected);
    }
}
