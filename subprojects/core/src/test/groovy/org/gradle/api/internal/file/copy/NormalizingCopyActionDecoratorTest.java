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
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
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

import static org.gradle.api.internal.file.TestFiles.fileSystem;
import static org.gradle.api.internal.file.copy.CopyActionExecuterUtil.visit;

@RunWith(JMock.class)
public class NormalizingCopyActionDecoratorTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final CopyActionProcessingStreamAction delegateAction = context.mock(CopyActionProcessingStreamAction.class);
    private final CopyAction delegate = new CopyAction() {
        public WorkResult execute(CopyActionProcessingStream stream) {
            stream.process(delegateAction);
            return new SimpleWorkResult(true);
        }
    };
    private final NormalizingCopyActionDecorator decorator = new NormalizingCopyActionDecorator(delegate, fileSystem());

    @Test
    public void doesNotVisitADirectoryWhichHasBeenVisitedBefore() {
        final FileCopyDetailsInternal details = file("dir", true, true);
        final FileCopyDetailsInternal file = file("dir/file", false, true);

        context.checking(new Expectations() {{
            one(delegateAction).processFile(details);
            one(delegateAction).processFile(file);
        }});

        visit(decorator, details, file, details);
    }

    @Test
    public void visitsDirectoryAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal dir1 = file("a/b/c", true, true);
        final FileCopyDetailsInternal file1 = file("a/b/c/file", false, true);

        decorator.execute(new CopyActionProcessingStream() {
            public void process(CopyActionProcessingStreamAction action) {
                context.checking(new Expectations() {{
                    one(delegateAction).processFile(with(hasPath("a")));
                    one(delegateAction).processFile(with(hasPath("a/b")));
                    one(delegateAction).processFile(dir1);
                    one(delegateAction).processFile(file1);
                }});

                action.processFile(dir1);
                action.processFile(file1);

                final FileCopyDetailsInternal dir2 = file("a/b/d/e", true, true);
                final FileCopyDetailsInternal file2 = file("a/b/d/e/file", false, true);

                context.checking(new Expectations() {{
                    one(delegateAction).processFile(with(hasPath("a/b/d")));
                    one(delegateAction).processFile(dir2);
                    one(delegateAction).processFile(file2);
                }});

                action.processFile(dir2);
                action.processFile(file2);
            }
        });
    }

    @Test
    public void visitsFileAncestorsWhichHaveNotBeenVisited() {
        final FileCopyDetailsInternal details = file("a/b/c", false, true);

        context.checking(new Expectations() {{
            one(delegateAction).processFile(with(hasPath("a")));
            one(delegateAction).processFile(with(hasPath("a/b")));
            one(delegateAction).processFile(details);
        }});

        visit(decorator, details);
    }

    @Test
    public void visitsAnEmptyDirectoryIfCorrespondingOptionIsOn() {
        final FileCopyDetailsInternal dir = file("dir", true, true);

        context.checking(new Expectations() {{
            one(delegateAction).processFile(dir);
        }});

        visit(decorator, dir);
    }

    @Test
    public void doesNotVisitAnEmptyDirectoryIfCorrespondingOptionIsOff() {
        final FileCopyDetailsInternal dir = file("dir", true, false);

        context.checking(new Expectations() {{
            exactly(0).of(delegateAction).processFile(dir);
        }});

        visit(decorator, dir);
    }

    private FileCopyDetailsInternal file(final String path, final boolean isDir, final boolean includeEmptyDirs) {
        final FileCopyDetailsInternal details = context.mock(FileCopyDetailsInternal.class, path);
        context.checking(new Expectations() {{
            allowing(details).getRelativePath();
            will(returnValue(RelativePath.parse(false, path)));
            allowing(details).isDirectory();
            will(returnValue(isDir));
            allowing(details).isIncludeEmptyDirs();
            will(returnValue(includeEmptyDirs));
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
