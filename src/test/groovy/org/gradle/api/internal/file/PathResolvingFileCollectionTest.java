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

import groovy.lang.Closure;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
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
import java.util.List;

@RunWith(JMock.class)
public class PathResolvingFileCollectionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileResolver resolver = context.mock(FileResolver.class);

    @Test
    public void resolvesSpecifiedFilesAgainstProject() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        FileCollection collection = new PathResolvingFileCollection(resolver, "src1", "src2");

        context.checking(new Expectations() {{
            one(resolver).resolve("src1");
            will(returnValue(file1));
            one(resolver).resolve("src2");
            will(returnValue(file2));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolver).resolve('a');
            will(returnValue(file1));
            allowing(resolver).resolve('b');
            will(returnValue(file2));
        }});

        List<Character> files = toList('a');
        Closure closure = HelperUtil.returns(files);
        FileCollection collection = new PathResolvingFileCollection(resolver, closure);

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        files.add('b');

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyASingleFile() {
        Closure closure = HelperUtil.returns('a');
        final File file = new File("1");

        FileCollection collection = new PathResolvingFileCollection(resolver, closure);

        context.checking(new Expectations() {{
            one(resolver).resolve('a');
            will(returnValue(file));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file)));
    }

    @Test
    public void canUseACollectionToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolver).resolve("src1");
            will(returnValue(file1));
            allowing(resolver).resolve("src2");
            will(returnValue(file2));
        }});

        List<String> files = toList("src1");
        FileCollection collection = new PathResolvingFileCollection(resolver, files);

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        files.add("src2");

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAFileCollectionToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        final FileCollection src = context.mock(FileCollection.class);

        FileCollection collection = new PathResolvingFileCollection(resolver, toList((Object) src));

        context.checking(new Expectations() {{
            one(src).getFiles();
            will(returnValue(toLinkedSet(file1)));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        context.checking(new Expectations() {{
            one(src).getFiles();
            will(returnValue(toLinkedSet(file1, file2)));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }
}
