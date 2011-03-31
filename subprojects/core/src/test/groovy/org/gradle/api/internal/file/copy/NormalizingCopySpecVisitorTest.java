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

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class NormalizingCopySpecVisitorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final CopySpecVisitor delegate = context.mock(CopySpecVisitor.class);
    private final NormalizingCopySpecVisitor visitor = new NormalizingCopySpecVisitor(delegate);

    @Test
    public void doesNotVisitADirectoryWhichHasBeenVisitedBefore() {
        final FileVisitDetails dir = dir("dir");
        final FileVisitDetails file = file("dir/file");


        context.checking(new Expectations() {{
            one(delegate).visitDir(with(hasPath("dir")));
            one(delegate).visitFile(with(hasPath("dir/file")));
        }});

        visitor.visitDir(dir);
        visitor.visitFile(file);
        visitor.visitDir(dir);
    }

    @Test
    public void visitsEmptyDirectory() {
        final FileVisitDetails dir = dir("dir");

        context.checking(new Expectations() {{
            one(delegate).visitDir(with(hasPath("dir")));
        }});

        visitor.visitDir(dir);
    }

    @Test
    public void visitsDirectoryWithEmptyDirectory() {
        final FileVisitDetails dir = dir("dir");
        final FileVisitDetails emptyDir = dir("dir/dir");


        context.checking(new Expectations() {{
            one(delegate).visitDir(with(hasPath("dir")));
            one(delegate).visitDir(with(hasPath("dir/dir")));
        }});

        visitor.visitDir(emptyDir);
    }

    @Test
    public void visitsDirectoryAncestorsWhichHaveNotBeenVisited() {
        final FileVisitDetails dir1 = dir("a/b/c");
        final FileVisitDetails file1 = file("a/b/c/file");

        context.checking(new Expectations() {{
            one(delegate).visitDir(with(hasPath("a")));
            one(delegate).visitDir(with(hasPath("a/b")));
            one(delegate).visitDir(with(hasPath("a/b/c")));
            one(delegate).visitFile(with(hasPath("a/b/c/file")));
        }});

        visitor.visitDir(dir1);
        visitor.visitFile(file1);

        final FileVisitDetails dir2 = dir("a/b/d/e");
        final FileVisitDetails file2 = file("a/b/d/e/file");

        context.checking(new Expectations() {{
            one(delegate).visitDir(with(hasPath("a/b/d")));
            one(delegate).visitDir(with(hasPath("a/b/d/e")));
            one(delegate).visitFile(with(hasPath("a/b/d/e/file")));
        }});

        visitor.visitDir(dir2);
        visitor.visitFile(file2);
    }


    @Test
    public void visitsFileAncestorsWhichHaveNotBeenVisited() {
        final FileVisitDetails details = dir("a/b/c");

        context.checking(new Expectations() {{
            one(delegate).visitDir(with(hasPath("a")));
            one(delegate).visitDir(with(hasPath("a/b")));
            one(delegate).visitFile(with(hasPath("a/b/c")));
        }});

        visitor.visitFile(details);
    }

    @Test
    public void visitSpecDelegatesToVisitor() {
        final ReadableCopySpec spec = context.mock(ReadableCopySpec.class);

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
        }});

        visitor.visitSpec(spec);
    }

    private FileVisitDetails file(final String path) {
        return createFileVisitDetails(path, true);
    }

    private FileVisitDetails dir(final String path) {
        return createFileVisitDetails(path, false);
    }

    private FileVisitDetails createFileVisitDetails(final String path, final boolean isFile) {
        final FileVisitDetails details = context.mock(FileVisitDetails.class, path);
        context.checking(new Expectations(){{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(isFile, path)));
        }});
        return details;
    }

    private Matcher<FileVisitDetails> hasPath(final String path) {
        return new BaseMatcher<FileVisitDetails>() {
            public void describeTo(Description description) {
                description.appendText("has path ").appendValue(path);
            }

            public boolean matches(Object o) {
                FileVisitDetails details = (FileVisitDetails) o;
                return details.getRelativePath().getPathString().equals(path);
            }
        };
    }
}
