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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class CopyVisitorTest {
    private File testDir;
    private File sourceDir;
    private final CopyVisitor visitor = new CopyVisitor();
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        testDir = tmpDir.getDir();
        sourceDir = getResource("testfiles");
        assertTrue(sourceDir.isDirectory());
    }

    private File getResource(String path) {
        URL resource = getClass().getResource(path);
        assertThat(String.format("Could not find resource '%s'", path), resource, notNullValue());
        assertThat(resource.getProtocol(), equalTo("file"));
        File result;
        try {
            result = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Could not locate test file '%s'.", path));
        }
        return result;
    }

    @Test
    public void plainCopy() {
        visitor.visitSpec(spec(testDir));

        visitor.visitDir(file(sourceDir, new RelativePath(false)));

        File rootFile = getResource("testfiles/rootfile.txt");
        File subDir = getResource("testfiles/subdir");
        File anotherFile = getResource("testfiles/subdir/anotherfile.txt");

        visitor.visitFile(file(rootFile, new RelativePath(true, rootFile.getName())));

        RelativePath subDirPath = new RelativePath(false, subDir.getName());
        visitor.visitDir(file(subDir, subDirPath));
        visitor.visitFile(file(anotherFile, new RelativePath(true, subDirPath, anotherFile.getName())));

        File targetRootFile = new File(testDir, rootFile.getName());
        assertTrue(targetRootFile.exists());

        File targetAnotherFile = new File(testDir, "subdir/" + anotherFile.getName());
        assertTrue(targetAnotherFile.exists());
    }

    @Test
    public void testThrowsExceptionWhenNoDestinationSet() {
        try {
            visitor.visitSpec(spec(null));
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("No copy destination directory has been specified, use 'into' to specify a target directory."));
        }
    }

    private CopySpecImpl spec(final File destDir) {
        return new TestCopySpecImpl(destDir);
    }

    private FileVisitDetails file(final File sourceDir, final RelativePath relativePath) {
        return new TestFileVisitDetails(sourceDir, relativePath);
    }

    private class TestFileVisitDetails extends DefaultFileTreeElement implements FileVisitDetails {
        public TestFileVisitDetails(File sourceDir, RelativePath relativePath) {
            super(sourceDir, relativePath);
        }

        public void stopVisiting() {
            throw new UnsupportedOperationException();
        }
    }

    private static class TestCopySpecImpl extends CopySpecImpl {
        private final File destDir;

        public TestCopySpecImpl(File destDir) {
            super(null);
            this.destDir = destDir;
        }

        @Override
        public File getDestDir() {
            return destDir;
        }
    }
}
