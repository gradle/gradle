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

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.HelperUtil;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class MappingCopySpecVisitorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();

    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final CopySpecVisitor delegate = context.mock(CopySpecVisitor.class);
    private final ReadableCopySpec spec = context.mock(ReadableCopySpec.class);
    private final FileVisitDetails details = context.mock(FileVisitDetails.class);
    private final FileSystem fileSystem = context.mock(FileSystem.class);
    private final MappingCopySpecVisitor visitor = new MappingCopySpecVisitor(delegate, fileSystem);

    @Test
    public void delegatesStartAndEndVisitMethods() {
        final CopyAction action = context.mock(CopyAction.class);

        context.checking(new Expectations() {{
            one(delegate).startVisit(action);
            one(delegate).endVisit();
        }});

        visitor.startVisit(action);
        visitor.endVisit();
    }

    @Test
    public void delegatesDidWork() {
        context.checking(new Expectations() {{
            allowing(delegate).getDidWork();
            will(onConsecutiveCalls(returnValue(true), returnValue(false)));
        }});

        assertTrue(visitor.getDidWork());
        assertFalse(visitor.getDidWork());
    }

    @Test
    public void visitFileInvokesEachCopyAction() {
        @SuppressWarnings("unchecked")
        final Action<FileCopyDetails> action1 = context.mock(Action.class, "action1");
        @SuppressWarnings("unchecked")
        final Action<FileCopyDetails> action2 = context.mock(Action.class, "action2");
        final Collector<FileCopyDetails> collectDetails1 = collector();
        final Collector<Object> collectDetails2 = collector();
        final Collector<Object> collectDetails3 = collector();

        context.checking(new Expectations() {{
            Sequence seq = context.sequence("seq");
            one(delegate).visitSpec(spec);
            inSequence(seq);

            allowing(spec).getAllCopyActions();
            will(returnValue(toList(action1, action2)));

            one(action1).execute(with(notNullValue(FileCopyDetails.class)));
            inSequence(seq);
            will(collectTo(collectDetails1));

            one(action2).execute(with(notNullValue(FileCopyDetails.class)));
            inSequence(seq);
            will(collectTo(collectDetails2));

            one(delegate).visitFile(with(not(sameInstance(details))));
            inSequence(seq);
            will(collectTo(collectDetails3));
        }});

        visitor.visitSpec(spec);
        visitor.visitFile(details);

        assertThat(collectDetails1.get(), sameInstance(collectDetails2.get()));
        assertThat(collectDetails1.get(), sameInstance(collectDetails3.get()));
    }

    @Test
    public void initialRelativePathForFileIsSpecPathPlusFilePath() {
        FileCopyDetails copyDetails = expectActionExecutedWhenFileVisited();

        context.checking(new Expectations() {{
            allowing(spec).getDestPath();
            will(returnValue(new RelativePath(false, "spec")));
            allowing(details).getRelativePath();
            will(returnValue(new RelativePath(true, "file")));
        }});

        assertThat(copyDetails.getRelativePath(), equalTo(new RelativePath(true, "spec", "file")));
    }

    @Test
    public void relativePathForDirIsSpecPathPlusFilePath() {
        FileVisitDetails visitDetails = expectSpecAndDirVisited();

        context.checking(new Expectations() {{
            allowing(spec).getDestPath();
            will(returnValue(new RelativePath(false, "spec")));
            allowing(details).getRelativePath();
            will(returnValue(new RelativePath(false, "dir")));
        }});

        assertThat(visitDetails.getRelativePath(), equalTo(new RelativePath(false, "spec", "dir")));
    }

    @Test
    public void copyActionCanChangeFileDestinationPath() {
        FileCopyDetails copyDetails = expectActionExecutedWhenFileVisited();

        RelativePath newPath = new RelativePath(true, "new");
        copyDetails.setRelativePath(newPath);
        assertThat(copyDetails.getRelativePath(), equalTo(newPath));

        copyDetails.setPath("/a/b");
        assertThat(copyDetails.getRelativePath(), equalTo(new RelativePath(true, "a", "b")));

        copyDetails.setName("new name");
        assertThat(copyDetails.getRelativePath(), equalTo(new RelativePath(true, "a", "new name")));
    }

    @Test
    public void copyActionCanExcludeFile() {
        @SuppressWarnings("unchecked")
        final Action<FileCopyDetails> action1 = context.mock(Action.class, "action1");
        @SuppressWarnings("unchecked")
        final Action<FileCopyDetails> action2 = context.mock(Action.class, "action2");

        context.checking(new Expectations() {{
            Sequence seq = context.sequence("seq");
            one(delegate).visitSpec(spec);
            inSequence(seq);

            allowing(spec).getAllCopyActions();
            will(returnValue(toList(action1, action2)));

            one(action1).execute(with(notNullValue(FileCopyDetails.class)));
            inSequence(seq);
            will(excludeFile());
        }});

        visitor.visitSpec(spec);
        visitor.visitFile(details);
    }

    @Test
    public void copyActionCanFilterContentWhenFileIsCopiedToStream() {
        final FileCopyDetails mappedDetails = expectActionExecutedWhenFileVisited();

        context.checking(new Expectations() {{
            one(details).open();
            will(returnValue(new ByteArrayInputStream("content".getBytes())));
        }});

        mappedDetails.filter(HelperUtil.toClosure("{ 'PREFIX: ' + it } "));

        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        mappedDetails.copyTo(outstr);
        assertThat(new String(outstr.toByteArray()), equalTo("PREFIX: content"));
    }

    @Test
    public void copyActionCanFilterContentWhenFileIsCopiedToFile() {
        final FileCopyDetails mappedDetails = expectActionExecutedWhenFileVisited();

        // shortcut the permission logic by explicitly setting permissions
        mappedDetails.setMode(0644);

        context.checking(new Expectations() {{

            one(details).open();
            will(returnValue(new ByteArrayInputStream("content".getBytes())));
            one(details).isDirectory();
            will(returnValue(false));
            one(details).getLastModified();
            will(returnValue(90L));
        }});

        mappedDetails.filter(HelperUtil.toClosure("{ 'PREFIX: ' + it } "));

        TestFile destDir = tmpDir.getTestDirectory().file("test.txt");
        mappedDetails.copyTo(destDir);
        destDir.assertContents(equalTo("PREFIX: content"));
    }

    @Test
    public void explicitFileModeDefinitionIsAppliedToTarget() throws IOException {
        final FileCopyDetails mappedDetails = expectActionExecutedWhenFileVisited();
        final TestFile destFile = tmpDir.getTestDirectory().file("test.txt").createFile();

        // set file permissions explicitly
        mappedDetails.setMode(0645);
        context.checking(new Expectations() {{
            one(details).copyTo(destFile);
            will(returnValue(true));
            one(fileSystem).chmod(destFile, 0645);
        }});
        mappedDetails.copyTo(destFile);
    }

    @Test
    public void getSizeReturnsSizeOfFilteredContent() {
        final FileCopyDetails mappedDetails = expectActionExecutedWhenFileVisited();

        context.checking(new Expectations() {{
            one(details).open();
            will(returnValue(new ByteArrayInputStream("content".getBytes())));
        }});

        mappedDetails.filter(HelperUtil.toClosure("{ 'PREFIX: ' + it } "));

        assertThat(mappedDetails.getSize(), equalTo(15L));
    }

    @Test
    public void wrappedFileElementDelegatesToSourceForRemainingMethods() {
        final FileVisitDetails mappedDetails = expectSpecAndFileVisited();
        final File file = new File("file");

        context.checking(new Expectations() {{
            one(details).getFile();
            will(returnValue(file));
        }});

        assertThat(mappedDetails.getFile(), sameInstance(file));
    }

    @Test
    public void permissionsArePreservedByDefault() {
        FileCopyDetails copyDetails = expectActionExecutedWhenFileVisited();

        context.checking(new Expectations() {{
            one(details).isDirectory();
            will(returnValue(true));

            one(spec).getDirMode();
            will(returnValue(null));

            one(details).getMode();
            will(returnValue(123));
        }});

        assertThat(copyDetails.getMode(), equalTo(123));
    }

    @Test
    public void filePermissionsCanBeOverriddenBySpec() {
        FileCopyDetails copyDetails = expectActionExecutedWhenFileVisited();

        context.checking(new Expectations() {{
            one(details).isDirectory();
            will(returnValue(false));

            one(spec).getFileMode();
            will(returnValue(234));
        }});

        assertThat(copyDetails.getMode(), equalTo(234));
    }


    @Test
    public void directoryPermissionsCanBeOverriddenBySpec() {
        FileCopyDetails copyDetails = expectActionExecutedWhenFileVisited();

        context.checking(new Expectations() {{
            one(details).isDirectory();
            will(returnValue(true));

            one(spec).getDirMode();
            will(returnValue(345));
        }});

        assertThat(copyDetails.getMode(), equalTo(345));
    }

    @Test
    public void permissionsCanBeOverriddenByCopyAction() {
        FileCopyDetails copyDetails = expectActionExecutedWhenFileVisited();

        copyDetails.setMode(456);
        assertThat(copyDetails.getMode(), equalTo(456));
    }

    private FileVisitDetails expectSpecAndFileVisited() {
        final Collector<FileVisitDetails> collector = collector();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);

            one(spec).getAllCopyActions();
            will(returnValue(toList()));

            one(delegate).visitFile(with(not(sameInstance(details))));
            will(collectTo(collector));
        }});

        visitor.visitSpec(spec);
        visitor.visitFile(details);
        return collector.get();
    }

    private FileCopyDetails expectActionExecutedWhenFileVisited() {
        final Collector<FileCopyDetails> collectDetails = collector();
        @SuppressWarnings("unchecked")
        final Action<FileCopyDetails> action = context.mock(Action.class, "action1");

        context.checking(new Expectations() {{
            Sequence seq = context.sequence("seq");
            one(delegate).visitSpec(spec);
            inSequence(seq);

            allowing(spec).getAllCopyActions();
            will(returnValue(toList(action)));

            one(action).execute(with(notNullValue(FileCopyDetails.class)));
            inSequence(seq);
            will(collectTo(collectDetails));

            one(delegate).visitFile(with(not(sameInstance(details))));
            inSequence(seq);
        }});

        visitor.visitSpec(spec);
        visitor.visitFile(details);

        return collectDetails.get();
    }

    private FileVisitDetails expectSpecAndDirVisited() {
        final Collector<FileVisitDetails> collector = collector();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
            one(delegate).visitDir(with(not(sameInstance(details))));

            will(collectTo(collector));
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(details);

        return collector.get();
    }

    private org.jmock.api.Action excludeFile() {
        return new org.jmock.api.Action() {
            public void describeTo(Description description) {
                description.appendText("exclude file");
            }

            public Object invoke(Invocation invocation) throws Throwable {
                FileCopyDetails details = (FileCopyDetails) invocation.getParameter(0);
                details.exclude();
                return null;
            }
        };
    }
}
