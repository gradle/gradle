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
package org.gradle.api.internal.artifacts;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.artifacts.FileCollection;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(JMock.class)
public class PathResolvingFileCollectionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Project project = context.mock(Project.class);

    @Test
    public void resolvesSpecifiedFilesAgainstProject() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        FileCollection collection = new PathResolvingFileCollection(project, "src1", "src2");

        context.checking(new Expectations() {{
            one(project).file("src1");
            will(returnValue(file1));
            one(project).file("src2");
            will(returnValue(file2));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyTheContentsOfTheCollection() {
        Closure closure = HelperUtil.returns(toList('a', 'b'));
        final File file1 = new File("1");
        final File file2 = new File("2");

        FileCollection collection = new PathResolvingFileCollection(project, closure);

        context.checking(new Expectations() {{
            one(project).file('a');
            will(returnValue(file1));
            one(project).file('b');
            will(returnValue(file2));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyASingleFile() {
        Closure closure = HelperUtil.returns('a');
        final File file = new File("1");

        FileCollection collection = new PathResolvingFileCollection(project, closure);

        context.checking(new Expectations() {{
            one(project).file('a');
            will(returnValue(file));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file)));
    }

    @Test
    public void canUseACollectionToSpecifyTheContentsOfTheColleciton() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        FileCollection collection = new PathResolvingFileCollection(project, toList("src1", "src2"));

        context.checking(new Expectations() {{
            one(project).file("src1");
            will(returnValue(file1));
            one(project).file("src2");
            will(returnValue(file2));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAFileCollectionToSpecifyTheContentsOfTheColleciton() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        final FileCollection src = context.mock(FileCollection.class);

        FileCollection collection = new PathResolvingFileCollection(project, toList((Object) src));

        context.checking(new Expectations() {{
            one(src).getFiles();
            will(returnValue(toLinkedSet(file1, file2)));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }
}
