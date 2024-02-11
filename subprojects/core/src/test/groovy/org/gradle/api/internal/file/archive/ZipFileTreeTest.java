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
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.internal.provider.Providers;
import org.gradle.cache.internal.TestCaches;
import org.gradle.test.fixtures.file.TestFile;
import org.junit.Test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.file.FileVisitorUtil.assertVisitsPermissions;
import static org.gradle.api.internal.file.TestFiles.directoryFileTreeFactory;
import static org.gradle.api.internal.file.TestFiles.fileHasher;
import static org.gradle.api.internal.file.TestFiles.fileSystem;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class ZipFileTreeTest extends AbstractArchiveFileTreeTest {
    private final TestFile archiveFile = tempDirProvider.getTestDirectory().file("test.zip");
    private final ZipFileTree tree = zipTree(archiveFile);

    private ZipFileTree zipTree(File archiveFile) {
        return new ZipFileTree(Providers.of(archiveFile),
            fileSystem(),
            directoryFileTreeFactory(),
            fileHasher(),
            TestCaches.decompressionCache(tempDirProvider.getTestDirectory().createDir("cache-dir")),
            TestFiles.tmpDirTemporaryFileProvider(tempDirProvider.getTestDirectory()));
    }

    @Override
    protected void archiveFileToRoot(TestFile file) {
        rootDir.zipTo(file);
    }

    @Override
    protected TestFile getArchiveFile() {
        return archiveFile;
    }

    @Override
    protected ZipFileTree getTree() {
        return tree;
    }

    @Test
    public void failsWhenArchiveFileDoesNotExist() {
        try {
            getTree().visit(new EmptyFileVisitor());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + archiveFile + "' as it does not exist."));
        }
    }

    @Test
    public void failsWhenArchiveFileIsADirectory() {
        getArchiveFile().createDir();

        try {
            getTree().visit(new EmptyFileVisitor());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + archiveFile + "' as it is not a file."));
        }
    }

    @Test
    public void wrapsFailureToUnarchiveFile() {
        getArchiveFile().write("not an archive file");

        try {
            getTree().visit(new EmptyFileVisitor());
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand ZIP '" + archiveFile + "'."));
        }
    }

    @Test
    public void expectedFilePermissionsAreFound() {
        resources.findResource("permissions.zip").copyTo(archiveFile);

        final Map<String, Integer> expected = new HashMap<>();
        expected.put("file", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }

    @Test
    public void expectedDefaultForNoModeZips() {
        resources.findResource("nomodeinfos.zip").copyTo(archiveFile);

        final Map<String, Integer> expected = new HashMap<>();
        expected.put("file.txt", 0644);
        expected.put("folder", 0755);

        assertVisitsPermissions(tree, expected);
    }
}
