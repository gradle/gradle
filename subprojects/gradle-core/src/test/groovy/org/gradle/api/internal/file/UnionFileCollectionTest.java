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

import org.gradle.api.file.FileCollection;
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
public class UnionFileCollectionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileCollection source1 = context.mock(FileCollection.class, "source1");
    private final FileCollection source2 = context.mock(FileCollection.class, "source2");

    @Test
    public void containsUnionOfAllSourceCollections() {
        final File file1 = new File("1");
        final File file2 = new File("2");
        final File file3 = new File("3");

        context.checking(new Expectations() {{
            one(source1).getFiles();
            will(returnValue(toSet(file1, file2)));
            one(source2).getFiles();
            will(returnValue(toSet(file2, file3)));
        }});

        UnionFileCollection collection = new UnionFileCollection(source1, source2);
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2, file3)));
    }

    @Test
    public void contentsTrackContentsOfSourceCollections() {
        final File file1 = new File("1");
        final File file2 = new File("2");
        final File file3 = new File("3");

        context.checking(new Expectations() {{
            allowing(source1).getFiles();
            will(returnValue(toSet(file1)));
            exactly(2).of(source2).getFiles();
            will(onConsecutiveCalls(returnValue(toSet(file2, file3)), returnValue(toSet(file3))));
        }});

        UnionFileCollection collection = new UnionFileCollection(source1, source2);
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2, file3)));
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file3)));
    }

    @Test
    public void canAddCollection() {
        final UnionFileCollection collection = new UnionFileCollection();
        collection.add(source1);
        assertThat(collection.getSourceCollections(), equalTo((Iterable) toList((Object) source1)));
    }
}
