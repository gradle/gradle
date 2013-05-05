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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
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
    private File destDir;
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileCopySpecVisitor visitor = new FileCopySpecVisitor();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() throws IOException {
        destDir = tmpDir.getTestDirectory().file("dest");
    }

    @Test
    public void plainCopy() {
        visitor.startVisit(action(destDir));

        visitor.visitDir(file(new RelativePath(false), destDir));

        visitor.visitFile(file(new RelativePath(true, "rootfile.txt"), new File(destDir, "rootfile.txt")));

        visitor.visitDir(file(new RelativePath(false, "subdir"), new File(destDir, "subdir")));

        visitor.visitFile(file(new RelativePath(true, "subdir", "anotherfile.txt"), new File(destDir, "subdir/anotherfile.txt")));
    }

    @Test
    public void testThrowsExceptionWhenNoDestinationSet() {
        try {
            visitor.startVisit(action(null));
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("No copy destination directory has been specified, use 'into' to specify a target directory."));
        }
    }

    private FileCopyAction action(final File destDir) {
        final FileCopyAction action = context.mock(FileCopyAction.class);
        context.checking(new Expectations(){{
            allowing(action).getDestinationDir();
            will(returnValue(destDir));
        }});
        return action;
    }

    private FileVisitDetails file(final RelativePath relativePath, final File targetFile) {
        final FileVisitDetails details = context.mock(FileVisitDetails.class, relativePath.getPathString());
        context.checking(new Expectations(){{
            allowing(details).getRelativePath();
            will(returnValue(relativePath));
            one(details).copyTo(targetFile);
        }});
        return details;
    }
}
