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

import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class NormalizingCopySpecVisitorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final CopySpecContentVisitor delegate = context.mock(CopySpecContentVisitor.class);
    private final NormalizingCopySpecContentVisitor visitor = new NormalizingCopySpecContentVisitor(delegate);
    private final CopySpecInternal spec = context.mock(CopySpecInternal.class);

    private void allowGetIncludeEmptyDirs() {
        context.checking(new Expectations() {{
            allowing(spec).getIncludeEmptyDirs();
            will(returnValue(true));
        }});
    }

    @Test
    public void doesNotVisitADirectoryWhichHasBeenVisitedBefore() {
        final FileCopyDetailsInternal details = file("dir", true);
        final FileCopyDetailsInternal file = file("dir/file", false);

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visit(details);
            one(delegate).visit(file);
        }});

        visitor.visit(details);
        visitor.visit(file);
        visitor.visit(details);
    }

    @Test
    public void doesNotVisitADirectoryUntilAChildFileIsVisited() {
        final FileCopyDetailsInternal dir = file("dir", true);
        final FileCopyDetailsInternal file = file("dir/file", false);

        allowGetIncludeEmptyDirs();

        visitor.visit(dir);

        context.checking(new Expectations() {{
            one(delegate).visit(dir);
            one(delegate).visit(file);
        }});

        visitor.visit(file);
    }

    @Test
    public void doesNotVisitADirectoryUntilAChildDirIsVisited() {
        final FileCopyDetailsInternal dir = file("dir", true);
        final FileCopyDetailsInternal subdir = file("dir/sub", true);
        final FileCopyDetailsInternal file = file("dir/sub/file", false);

        allowGetIncludeEmptyDirs();

        visitor.visit(dir);
        visitor.visit(subdir);

        context.checking(new Expectations() {{
            one(delegate).visit(dir);
            one(delegate).visit(subdir);
            one(delegate).visit(file);
        }});

        visitor.visit(file);
    }

    @Test
    public void visitsDirectoryAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal dir1 = file("a/b/c", true);
        final FileCopyDetailsInternal file1 = file("a/b/c/file", false);

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visit(with(hasPath("a")));
            one(delegate).visit(with(hasPath("a/b")));
            one(delegate).visit(dir1);
            one(delegate).visit(file1);
        }});

        visitor.visit(dir1);
        visitor.visit(file1);

        final FileCopyDetailsInternal dir2 = file("a/b/d/e", true);
        final FileCopyDetailsInternal file2 = file("a/b/d/e/file", false);

        context.checking(new Expectations() {{
            one(delegate).visit(with(hasPath("a/b/d")));
            one(delegate).visit(dir2);
            one(delegate).visit(file2);
        }});

        visitor.visit(dir2);
        visitor.visit(file2);
    }

    @Test
    public void visitsFileAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal details = file("a/b/c", false);

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visit(with(hasPath("a")));
            one(delegate).visit(with(hasPath("a/b")));
            one(delegate).visit(details);
        }});

        visitor.visit(details);
    }

    @Test
    public void visitsAnEmptyDirectoryIfCorrespondingOptionIsOn() {
        final FileCopyDetailsInternal dir = file("dir", true);

        context.checking(new Expectations() {{
            one(spec).getIncludeEmptyDirs();
            will(returnValue(true));
            one(delegate).visit(dir);
            one(delegate).endVisit();
        }});

        visitor.visit(dir);
        visitor.endVisit();
    }

    @Test
    public void doesNotVisitAnEmptyDirectoryIfCorrespondingOptionIsOff() {
        FileCopyDetailsInternal dir = file("dir", true);

        context.checking(new Expectations() {{
            one(spec).getIncludeEmptyDirs();
            will(returnValue(false));
            one(delegate).endVisit();
        }});

        visitor.visit(dir);
        visitor.endVisit();
    }

    private FileCopyDetailsInternal file(final String path, final boolean isDir) {
        final FileCopyDetailsInternal details = context.mock(FileCopyDetailsInternal.class, path);
        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(false, path)));
            allowing(details).isDirectory();
            will(returnValue(isDir));
            allowing(details).getCopySpec();
            will(returnValue(spec));
        }});
        return details;
    }

    private Matcher<FileCopyDetailsInternal> hasPath(final String path) {
        return new BaseMatcher<FileCopyDetailsInternal>() {
            public void describeTo(Description description) {
                description.appendText("has path ").appendValue(path);
            }

            public boolean matches(Object o) {
                FileCopyDetails details = (FileCopyDetails) o;
                return details.getRelativePath().getPathString().equals(path);
            }
        };
    }
}
