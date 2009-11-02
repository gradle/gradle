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

import org.apache.tools.ant.filters.PrefixLines;
import org.gradle.api.file.CopyAction;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.integtests.TestFile;
import static org.gradle.util.Matchers.*;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

@RunWith(JMock.class)
public class MappingCopySpecVisitorTest {
    private final JUnit4Mockery context = new JUnit4Mockery(){{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    @Rule
    public final TemporaryFolder tmpDir = new TemporaryFolder();
    private final CopySpecVisitor delegate = context.mock(CopySpecVisitor.class);
    private final MappingCopySpecVisitor visitor = new MappingCopySpecVisitor(delegate);
    private final CopySpecImpl spec = context.mock(CopySpecImpl.class);
    private final FileVisitDetails details = context.mock(FileVisitDetails.class);

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
    public void visitFileWrapsFileElementToMapName() {
        final Collector<FileVisitDetails> collector = expectSpecAndFileVisited();

        final CopyDestinationMapper mapper = context.mock(CopyDestinationMapper.class);
        final RelativePath specPath = new RelativePath(false, "spec");
        final RelativePath relativePath = new RelativePath(true, "file");

        context.checking(new Expectations() {{
            allowing(spec).getDestinationMapper();
            will(returnValue(mapper));
            allowing(spec).getDestPath();
            will(returnValue(specPath));
            one(mapper).getPath(details);
            will(returnValue(relativePath));
        }});

        FileVisitDetails mappedDetails = collector.get();

        assertThat(mappedDetails.getRelativePath(), equalTo(new RelativePath(true, specPath, "file")));
    }

    @Test
    public void visitFileWrapsFileElementToFilterContentStream() {
        final Collector<FileVisitDetails> collector = expectSpecAndFileVisited();

        final FilterChain filterChain = new FilterChain();
        PrefixLines filter = new PrefixLines(filterChain.getLastFilter());
        filter.setPrefix("PREFIX: ");
        filterChain.addFilter(filter);

        context.checking(new Expectations() {{
            allowing(spec).getFilterChain();
            will(returnValue(filterChain));
            one(details).open();
            will(returnValue(new ByteArrayInputStream("content".getBytes())));
        }});

        FileVisitDetails mappedDetails = collector.get();

        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        mappedDetails.copyTo(outstr);
        assertThat(new String(outstr.toByteArray()), equalTo("PREFIX: content"));
    }

    @Test
    public void visitFileWrapsFileElementToFilterContentFile() {
        final Collector<FileVisitDetails> collector = expectSpecAndFileVisited();

        final FilterChain filterChain = new FilterChain();
        PrefixLines filter = new PrefixLines(filterChain.getLastFilter());
        filter.setPrefix("PREFIX: ");
        filterChain.addFilter(filter);

        context.checking(new Expectations() {{
            allowing(spec).getFilterChain();
            will(returnValue(filterChain));
            one(details).open();
            will(returnValue(new ByteArrayInputStream("content".getBytes())));
            one(details).isDirectory();
            will(returnValue(false));
            one(details).getLastModified();
            will(returnValue(90L));
        }});

        FileVisitDetails mappedDetails = collector.get();

        TestFile destDir = tmpDir.getDir().file("test.txt");
        mappedDetails.copyTo(destDir);
        destDir.assertContents(equalTo("PREFIX: content"));
    }

    @Test
    public void wrappedFileElementDelegatesToSourceForRemainingMethods() {
        final Collector<FileVisitDetails> collector = expectSpecAndFileVisited();
        final File file = new File("file");

        context.checking(new Expectations() {{
            one(details).getFile();
            will(returnValue(file));
        }});

        FileVisitDetails mappedDetails = collector.get();

        assertThat(mappedDetails.getFile(), sameInstance(file));
    }

    private Collector<FileVisitDetails> expectSpecAndFileVisited() {
        final Collector<FileVisitDetails> collector = collectParam();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
            one(delegate).visitFile(with(notNullValue(FileVisitDetails.class)));

            will(collector);
        }});

        visitor.visitSpec(spec);
        visitor.visitFile(details);
        return collector;
    }
}
