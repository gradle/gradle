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
        final FileCopyDetails details = file("dir");
        final FileCopyDetails file = file("dir/file");

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
            one(delegate).visitDir(details);
            one(delegate).visitFile(file);
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(details);
        visitor.visitFile(file);
        visitor.visitDir(details);
    }

    @Test
    public void doesNotVisitADirectoryUntilAChildFileIsVisited() {
        final FileCopyDetails dir = file("dir");
        final FileCopyDetails file = file("dir/file");

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(dir);

        context.checking(new Expectations() {{
            one(delegate).visitDir(dir);
            one(delegate).visitFile(file);
        }});

        visitor.visitFile(file);
    }

    @Test
    public void doesNotVisitADirectoryUntilAChildDirIsVisited() {
        final FileCopyDetails dir = file("dir");
        final FileCopyDetails subdir = file("dir/sub");
        final FileCopyDetails file = file("dir/sub/file");

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(dir);
        visitor.visitDir(subdir);

        context.checking(new Expectations() {{
            one(delegate).visitDir(dir);
            one(delegate).visitDir(subdir);
            one(delegate).visitFile(file);
        }});

        visitor.visitFile(file);
    }

    @Test
    public void visitsDirectoryAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetails dir1 = file("a/b/c");
        final FileCopyDetails file1 = file("a/b/c/file");

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
            one(delegate).visitDir(with(hasPath("a")));
            one(delegate).visitDir(with(hasPath("a/b")));
            one(delegate).visitDir(dir1);
            one(delegate).visitFile(file1);
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(dir1);
        visitor.visitFile(file1);

        final FileCopyDetails dir2 = file("a/b/d/e");
        final FileCopyDetails file2 = file("a/b/d/e/file");

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
            one(delegate).visitDir(with(hasPath("a/b/d")));
            one(delegate).visitDir(dir2);
            one(delegate).visitFile(file2);
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(dir2);
        visitor.visitFile(file2);
    }

    @Test
    public void visitsFileAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetails details = file("a/b/c");

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
            one(delegate).visitDir(with(hasPath("a")));
            one(delegate).visitDir(with(hasPath("a/b")));
            one(delegate).visitFile(details);
        }});

        visitor.visitSpec(spec);
        visitor.visitFile(details);
    }

    @Test
    public void visitSpecDelegatesToVisitor() {
        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegate).visitSpec(spec);
        }});

        visitor.visitSpec(spec);
    }

    @Test
    public void visitsAnEmptyDirectoryIfCorrespondingOptionIsOn() {
        final FileCopyDetails dir = file("dir");

        context.checking(new Expectations() {{
            one(spec).getIncludeEmptyDirs();
            will(returnValue(true));
            one(delegate).visitSpec(spec);
            one(delegate).visitDir(dir);
            one(delegate).endVisit();
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(dir);
        visitor.endVisit();
    }

    @Test
    public void doesNotVisitAnEmptyDirectoryIfCorrespondingOptionIsOff() {
        FileCopyDetails dir = file("dir");

        context.checking(new Expectations() {{
            one(spec).getIncludeEmptyDirs();
            will(returnValue(false));
            one(delegate).visitSpec(spec);
            one(delegate).endVisit();
        }});

        visitor.visitSpec(spec);
        visitor.visitDir(dir);
        visitor.endVisit();
    }

    private FileCopyDetails file(final String path) {
        final FileCopyDetails details = context.mock(FileCopyDetails.class, path);
        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(false, path)));
        }});
        return details;
    }

    private Matcher<FileCopyDetails> hasPath(final String path) {
        return new BaseMatcher<FileCopyDetails>() {
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
