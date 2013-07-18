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
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.api.internal.file.copy.CopySpecContentVisitorTestDriver.visit;

@RunWith(JMock.class)
public class NormalizingCopyActionDecoratorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Action<FileCopyDetailsInternal> delegateAction = (Action<FileCopyDetailsInternal>) context.mock(Action.class);
    private final CopyAction delegate = new CopyAction() {
        public WorkResult execute(Action<Action<? super FileCopyDetailsInternal>> stream) {
            stream.execute(delegateAction);
            return new SimpleWorkResult(true);
        }
    };
    private final NormalizingCopyActionDecorator decorator = new NormalizingCopyActionDecorator(delegate);
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
            one(delegateAction).execute(details);
            one(delegateAction).execute(file);
        }});

        visit(decorator, details, file, details);
    }

    @Test
    public void visitsDirectoryAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal dir1 = file("a/b/c", true);
        final FileCopyDetailsInternal file1 = file("a/b/c/file", false);

        allowGetIncludeEmptyDirs();

        decorator.execute(new Action<Action<? super FileCopyDetailsInternal>>() {
            public void execute(Action<? super FileCopyDetailsInternal> action) {

                context.checking(new Expectations() {{
                    one(delegateAction).execute(with(hasPath("a")));
                    one(delegateAction).execute(with(hasPath("a/b")));
                    one(delegateAction).execute(dir1);
                    one(delegateAction).execute(file1);
                }});

                action.execute(dir1);
                action.execute(file1);

                final FileCopyDetailsInternal dir2 = file("a/b/d/e", true);
                final FileCopyDetailsInternal file2 = file("a/b/d/e/file", false);

                context.checking(new Expectations() {{
                    one(delegateAction).execute(with(hasPath("a/b/d")));
                    one(delegateAction).execute(dir2);
                    one(delegateAction).execute(file2);
                }});

                action.execute(dir2);
                action.execute(file2);
            }
        });
    }

    @Test
    public void visitsFileAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal details = file("a/b/c", false);

        allowGetIncludeEmptyDirs();

        context.checking(new Expectations() {{
            one(delegateAction).execute(with(hasPath("a")));
            one(delegateAction).execute(with(hasPath("a/b")));
            one(delegateAction).execute(details);
        }});

        visit(decorator, details);
    }

    @Test
    public void visitsAnEmptyDirectoryIfCorrespondingOptionIsOn() {
        final FileCopyDetailsInternal dir = file("dir", true);

        context.checking(new Expectations() {{
            one(spec).getIncludeEmptyDirs();
            will(returnValue(true));
            one(delegateAction).execute(dir);
        }});

        visit(decorator, dir);
    }

    @Test
    public void doesNotVisitAnEmptyDirectoryIfCorrespondingOptionIsOff() {
        final FileCopyDetailsInternal dir = file("dir", true);

        context.checking(new Expectations() {{
            one(spec).getIncludeEmptyDirs();
            will(returnValue(false));
            exactly(0).of(delegateAction).execute(dir);
        }});

        visit(decorator, dir);
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
