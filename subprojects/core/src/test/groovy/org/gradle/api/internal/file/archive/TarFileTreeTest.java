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
import static org.gradle.api.file.FileVisitorUtil.*;
import static org.gradle.api.tasks.AntBuilderAwareUtil.*;
import org.gradle.util.TestFile;
import org.gradle.util.TemporaryFolder;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;

import static java.util.Collections.*;

public class TarFileTreeTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final TestFile tarFile = tmpDir.getDir().file("test.tar");
    private final TestFile rootDir = tmpDir.getDir().file("root");
    private final TestFile expandDir = tmpDir.getDir().file("tmp");
    private final TarFileTree tree = new TarFileTree(tarFile, expandDir);

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
    public void canStopVisitingFiles() {
        rootDir.file("subdir/file1.txt").write("content");
        rootDir.file("subdir/other/file2.txt").write("content");
        rootDir.tarTo(tarFile);

        assertCanStopVisiting(tree);
    }

    @Test
    public void isEmptyWhenTarFileDoesNotExist() {
        assertVisits(tree, EMPTY_LIST, EMPTY_LIST);
        assertSetContainsForAllTypes(tree, EMPTY_LIST);
    }

    @Test
    public void failsWhenTarFileIsADirectory() {
        tarFile.createDir();

        try {
            tree.visit(null);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot expand TAR '" + tarFile + "' as it is not a file."));
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
            assertThat(e.getMessage(), equalTo("Could not expand TAR '" + tarFile + "'."));
        }
    }
}