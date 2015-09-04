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
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class UnionFileTreeTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final UnionFileTree set = new UnionFileTree("<display name>");

    @Before
    public void setUp() {
        NativeServicesTestFixture.initialize();
    }

    @Test
    public void canAddFileTree() {
        FileTreeInternal set1 = context.mock(FileTreeInternal.class, "set1");

        set.add(set1);
        assertThat(set.getSourceCollections(), equalTo((Iterable) toList((Object) set1)));
    }

    @Test
    public void cannotAddFileCollection() {
        try {
            set.add(context.mock(FileCollection.class));
            fail();
        } catch (UnsupportedOperationException e) {
            assertThat(e.getMessage(), equalTo("Can only add FileTree instances to <display name>."));
        }
    }
}
