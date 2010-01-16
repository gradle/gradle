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
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.copy.ArchiveCopyAction;
import org.gradle.api.internal.file.copy.ReadableCopySpec;
import org.gradle.util.TestFile;
import org.gradle.util.TemporaryFolder;
import org.hamcrest.Description;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.OutputStream;

@RunWith(JMock.class)
public class ZipCopySpecVisitorTest {
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ArchiveCopyAction copyAction = context.mock(ArchiveCopyAction.class);
    private final ReadableCopySpec copySpec = context.mock(ReadableCopySpec.class);
    private final ZipCopySpecVisitor visitor = new ZipCopySpecVisitor();

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(copySpec).getFileMode();
            will(returnValue(1));
            allowing(copySpec).getDirMode();
            will(returnValue(2));
        }});
    }

    @Test
    public void createsZipFile() {
        final TestFile zipFile = tmpDir.getDir().file("test.zip");

        context.checking(new Expectations(){{
            allowing(copyAction).getArchivePath();
            will(returnValue(zipFile));
        }});

        visitor.startVisit(copyAction);
        visitor.visitSpec(copySpec);

        visitor.visitDir(dir("dir"));
        visitor.visitFile(file("dir/file1"));
        visitor.visitFile(file("file2"));

        visitor.endVisit();

        TestFile expandDir = tmpDir.getDir().file("expanded");
        zipFile.unzipTo(expandDir);
        expandDir.file("dir/file1").assertContents(equalTo("contents of dir/file1"));
        expandDir.file("file2").assertContents(equalTo("contents of file2"));
    }

    @Test
    public void wrapsFailureToOpenOutputFile() {
        final TestFile zipFile = tmpDir.createDir("test.zip");

        context.checking(new Expectations(){{
            allowing(copyAction).getArchivePath();
            will(returnValue(zipFile));
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
        final TestFile zipFile = tmpDir.getDir().file("test.zip");

        context.checking(new Expectations(){{
            allowing(copyAction).getArchivePath();
            will(returnValue(zipFile));
        }});

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

    private FileVisitDetails file(final String path) {
        final FileVisitDetails details = context.mock(FileVisitDetails.class, path);

        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(true, path)));

            allowing(details).getLastModified();
            will(returnValue(1000L));

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

    private FileVisitDetails dir(final String path) {
        final FileVisitDetails details = context.mock(FileVisitDetails.class, path);

        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(false, path)));

            allowing(details).getLastModified();
            will(returnValue(1000L));
        }});

        return details;
    }

    private FileVisitDetails brokenFile(final String path, final Throwable failure) {
        final FileVisitDetails details = context.mock(FileVisitDetails.class, String.format("[%s]", path));

        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(true, path)));

            allowing(details).getLastModified();
            will(returnValue(1000L));

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
