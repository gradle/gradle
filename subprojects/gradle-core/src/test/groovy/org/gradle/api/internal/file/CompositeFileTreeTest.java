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
import org.gradle.api.file.FileTree;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class CompositeFileTreeTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final FileTree source1 = context.mock(FileTree.class, "source1");
    private final FileTree source2 = context.mock(FileTree.class, "source2");
    private final CompositeFileTree tree = new CompositeFileTree() {
        @Override
        public String getDisplayName() {
            return "<display-name>";
        }

        @Override
        protected Iterable<? extends FileTree> getSourceCollections() {
            return toList(source1, source2);
        }
    };

    @Test
    public void matchingReturnsUnionOfFilteredSets() {
        final Closure closure = HelperUtil.TEST_CLOSURE;
        final FileTree filtered1 = context.mock(FileTree.class, "filtered1");
        final FileTree filtered2 = context.mock(FileTree.class, "filtered2");

        context.checking(new Expectations() {{
            one(source1).matching(closure);
            will(returnValue(filtered1));
            one(source2).matching(closure);
            will(returnValue(filtered2));
        }});

        FileTree filtered = tree.matching(closure);
        assertThat(filtered, instanceOf(CompositeFileTree.class));
        CompositeFileTree filteredCompositeSet = (CompositeFileTree) filtered;

        assertThat(toList(filteredCompositeSet.getSourceCollections()), equalTo(toList(filtered1, filtered2)));
    }

    @Test
    public void plusReturnsUnionOfThisTreeAndSourceTree() {
        FileTree other = context.mock(FileTree.class, "other");

        FileTree sum = tree.plus(other);
        assertThat(sum, instanceOf(CompositeFileTree.class));
        UnionFileTree sumCompositeTree = (UnionFileTree) sum;
        assertThat(sumCompositeTree.getSourceCollections(), equalTo(toLinkedSet(tree, other)));
    }
}
