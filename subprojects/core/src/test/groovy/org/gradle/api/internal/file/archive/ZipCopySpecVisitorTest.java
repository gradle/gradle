/*
 * Copyright 2010 the original author or authors.
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

import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.archive.compression.ArchiveOutputStreamFactory;
import org.gradle.api.internal.file.copy.ArchiveCopyAction;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.ZipDeflatedCompressor;
import org.gradle.api.internal.file.copy.ZipStoredCompressor;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.file.FileVisitorUtil.assertVisitsPermissions;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class ZipCopySpecVisitorTest {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ArchiveCopyAction copyAction = context.mock(ZipCopyAction.class);
    private final CopySpecInternal copySpec = context.mock(CopySpecInternal.class);
    private final ZipCopySpecContentVisitor visitor = new ZipCopySpecContentVisitor();
    private TestFile zipFile;

    @Before
    public void setup() {
        zipFile = tmpDir.getTestDirectory().file("test.zip");
        context.checking(new Expectations() {{
            allowing(copyAction).getArchivePath();
            will(returnValue(zipFile));
        }});
        context.checking(new Expectations() {{
            allowing(copyAction).getCompressor();
            will(returnValue(ZipStoredCompressor.INSTANCE));
        }});
    }

    private TestFile initializeZipFile(final TestFile testFile, final ArchiveOutputStreamFactory compressor) {
        context.checking(new Expectations() {{
            allowing(copyAction).getArchivePath();
            will(returnValue(zipFile));
            allowing(copyAction).getCompressor();
            will(returnValue(compressor));
        }});
        return testFile;
    }

    @Test
    public void createsZipFile() {
        initializeZipFile(zipFile, ZipStoredCompressor.INSTANCE);
        zip(dir("dir"), file("dir/file1"), file("file2"));

        TestFile expandDir = tmpDir.getTestDirectory().file("expanded");
        zipFile.unzipTo(expandDir);
        expandDir.file("dir/file1").assertContents(equalTo("contents of dir/file1"));
        expandDir.file("file2").assertContents(equalTo("contents of file2"));
    }

    @Test
    public void createsDeflatedZipFile() {
        initializeZipFile(zipFile, ZipDeflatedCompressor.INSTANCE);
        zip(dir("dir"), file("dir/file1"), file("file2"));

        TestFile expandDir = tmpDir.getTestDirectory().file("expanded");
        zipFile.unzipTo(expandDir);
        expandDir.file("dir/file1").assertContents(equalTo("contents of dir/file1"));
        expandDir.file("file2").assertContents(equalTo("contents of file2"));
    }

    @Test
    public void zipFileContainsExpectedPermissions() {
        zip(dir("dir"), file("file"));

        Map<String, Integer> expected = new HashMap<String, Integer>();
        expected.put("dir", 2);
        expected.put("file", 1);

        assertVisitsPermissions(new ZipFileTree(zipFile, null), expected);
    }

    @Test
    public void wrapsFailureToOpenOutputFile() {
        final TestFile invalidZipFile = tmpDir.createDir("test.zip");

        context.checking(new Expectations() {{
            allowing(copyAction).getArchivePath();
            will(returnValue(invalidZipFile));
        }});

        try {
            visitor.startVisit(copyAction);
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo(String.format("Could not create ZIP '%s'.", zipFile)));
        }
    }

    @Test
    public void wrapsFailureToAddElement() {
        visitor.startVisit(copyAction);
        visitor.visitSpec(copySpec);

        Throwable failure = new RuntimeException("broken");
        try {
            visitor.visitFile(brokenFile("dir/file1", failure));
            fail();
        } catch (GradleException e) {
            assertThat(e.getMessage(), equalTo(String.format("Could not add [dir/file1] to ZIP '%s'.", zipFile)));
            assertThat(e.getCause(), sameInstance(failure));
        }
    }

    private void zip(FileCopyDetails... files) {
        visitor.startVisit(copyAction);
        visitor.visitSpec(copySpec);

        for (FileCopyDetails f : files) {
            if (f.isDirectory()) {
                visitor.visitDir(f);
            } else {
                visitor.visitFile(f);
            }
        }

        visitor.endVisit();
    }

    private FileCopyDetails file(final String path) {
        final FileCopyDetails details = context.mock(FileCopyDetails.class, path);

        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(true, path)));

            allowing(details).getLastModified();
            will(returnValue(1000L));

            allowing(details).isDirectory();
            will(returnValue(false));

            allowing(details).getMode();
            will(returnValue(1));

            allowing(details).copyTo(with(notNullValue(OutputStream.class)));
            will(new Action() {
                public void describeTo(Description description) {
                    description.appendText("write content");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    IOUtils.write(String.format("contents of %s", path), (OutputStream) invocation.getParameter(0));
                    return null;
                }
            });
        }});

        return details;
    }

    private FileCopyDetails dir(final String path) {
        final FileCopyDetails details = context.mock(FileCopyDetails.class, path);

        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(false, path)));

            allowing(details).getLastModified();
            will(returnValue(1000L));

            allowing(details).isDirectory();
            will(returnValue(true));

            allowing(details).getMode();
            will(returnValue(2));
        }});

        return details;
    }

    private FileCopyDetails brokenFile(final String path, final Throwable failure) {
        final FileCopyDetails details = context.mock(FileCopyDetails.class, String.format("[%s]", path));

        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(true, path)));

            allowing(details).getLastModified();
            will(returnValue(1000L));

            allowing(details).isDirectory();
            will(returnValue(false));

            allowing(details).getMode();
            will(returnValue(1));

            allowing(details).copyTo(with(notNullValue(OutputStream.class)));
            will(new Action() {
                public void describeTo(Description description) {
                    description.appendText("write content");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    failure.fillInStackTrace();
                    throw failure;
                }
            });
        }});

        return details;
    }
}
