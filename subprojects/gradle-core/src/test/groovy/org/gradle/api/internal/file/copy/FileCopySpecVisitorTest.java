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

package org.gradle.api.internal.file.copy;

import org.apache.tools.zip.UnixStat;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class FileCopySpecVisitorTest {

    public static final int CUSTOM_FILE_MODE = 755;

    private File destDir;
    private TestFile sourceDir;
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws IOException {
        destDir = tmpDir.getDir().file("dest");
        sourceDir = tmpDir.getDir().file("src").createDir();
    }

    @Test
    public void plainCopy() {

        final File rootTargetFile = new File(destDir, "rootfile.txt");
        final File subDirTargetFile = new File(destDir, "subdir/anotherfile.txt");

        FileCopySpecVisitor visitor = new FileCopySpecVisitor() {
            @Override
            FileChmod getFileChmod() {
                final FileChmod fileChmod = context.mock(FileChmod.class);
                context.checking(new Expectations() {{
                    one(fileChmod).chmod(subDirTargetFile, CUSTOM_FILE_MODE);
                }});
                return fileChmod;
            }
        };

        visitor.startVisit(action(destDir));
        visitor.visitDir(file(new RelativePath(false)));
        visitor.visitSpec(spec());
        visitor.visitFile(file(new RelativePath(true, "rootfile.txt"), rootTargetFile));

        RelativePath subDirPath = new RelativePath(false, "subdir");
        visitor.visitDir(file(subDirPath));
        visitor.visitSpec(spec(CUSTOM_FILE_MODE));
        visitor.visitFile(file(new RelativePath(true, "subdir", "anotherfile.txt"), subDirTargetFile));
    }

    @Test
    public void testThrowsExceptionWhenNoDestinationSet() {
        FileCopySpecVisitor visitor = new FileCopySpecVisitor();
        try {
            visitor.startVisit(action(null));
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("No copy destination directory has been specified, use 'into' to specify a target directory."));
        }
    }

    private ReadableCopySpec spec() {
        return spec(UnixStat.DEFAULT_FILE_PERM);
    }

    private ReadableCopySpec spec(final int fileMode) {
        final ReadableCopySpec spec = context.mock(ReadableCopySpec.class, String.format("readableCopySpec%s", fileMode));
        context.checking(new Expectations() {{
            allowing(spec).getFileMode();
            will(returnValue(fileMode));
        }});
        return spec;
    }

    private FileCopyAction action(final File destDir) {
        final FileCopyAction action = context.mock(FileCopyAction.class);
        context.checking(new Expectations() {{
            allowing(action).getDestinationDir();
            will(returnValue(destDir));
        }});
        return action;
    }

    private FileVisitDetails file(final RelativePath relativePath) {
        final FileVisitDetails details = context.mock(FileVisitDetails.class, relativePath.getPathString());
        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(relativePath));
        }});
        return details;
    }

    private FileVisitDetails file(final RelativePath relativePath, final File targetFile) {
        final FileVisitDetails details = file(relativePath);
        context.checking(new Expectations() {{
            one(details).copyTo(targetFile);
        }});
        return details;
    }
}
