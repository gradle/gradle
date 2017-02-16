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
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Actions;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.TestUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class CompositeFileTreeTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final FileTreeInternal source1 = context.mock(FileTreeInternal.class);
    private final FileTreeInternal source2 = context.mock(FileTreeInternal.class);
    private final CompositeFileTree tree = new CompositeFileTree() {
        @Override
        public String getDisplayName() {
            return "<display-name>";
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            context.add(source1);
            context.add(source2);
        }
    };

    @Before
    public void setUp() {
        NativeServicesTestFixture.initialize();
    }

    @Test
    public void matchingWithClosureReturnsUnionOfFilteredSets() {
        final Closure closure = TestUtil.TEST_CLOSURE;
        final FileTreeInternal filtered1 = context.mock(FileTreeInternal.class);
        final FileTreeInternal filtered2 = context.mock(FileTreeInternal.class);

        context.checking(new Expectations() {{
            oneOf(source1).matching(closure);
            will(returnValue(filtered1));
            oneOf(source2).matching(closure);
            will(returnValue(filtered2));
        }});

        FileTree filtered = tree.matching(closure);
        assertThat(filtered, instanceOf(CompositeFileTree.class));
        CompositeFileTree filteredCompositeSet = (CompositeFileTree) filtered;

        assertThat(toList(filteredCompositeSet.getSourceCollections()), equalTo(toList(filtered1, filtered2)));
    }

    @Test
    public void matchingWithActionReturnsUnionOfFilteredSets() {
        final Action<PatternFilterable> action = Actions.doNothing();
        final FileTreeInternal filtered1 = context.mock(FileTreeInternal.class);
        final FileTreeInternal filtered2 = context.mock(FileTreeInternal.class);

        context.checking(new Expectations() {{
            oneOf(source1).matching(action);
            will(returnValue(filtered1));
            oneOf(source2).matching(action);
            will(returnValue(filtered2));
        }});

        FileTree filtered = tree.matching(action);
        assertThat(filtered, instanceOf(CompositeFileTree.class));
        CompositeFileTree filteredCompositeSet = (CompositeFileTree) filtered;

        assertThat(toList(filteredCompositeSet.getSourceCollections()), equalTo(toList(filtered1, filtered2)));
    }

    @Test
    public void matchingWithPatternSetReturnsUnionOfFilteredSets() {
        final PatternSet patternSet = new PatternSet();
        final FileTreeInternal filtered1 = context.mock(FileTreeInternal.class);
        final FileTreeInternal filtered2 = context.mock(FileTreeInternal.class);

        context.checking(new Expectations() {{
            oneOf(source1).matching(patternSet);
            will(returnValue(filtered1));
            oneOf(source2).matching(patternSet);
            will(returnValue(filtered2));
        }});

        FileTree filtered = tree.matching(patternSet);
        assertThat(filtered, instanceOf(CompositeFileTree.class));
        CompositeFileTree filteredCompositeSet = (CompositeFileTree) filtered;

        assertThat(toList(filteredCompositeSet.getSourceCollections()), equalTo(toList(filtered1, filtered2)));
    }

    @Test
    public void plusReturnsUnionOfThisTreeAndSourceTree() {
        FileTreeInternal other = context.mock(FileTreeInternal.class);

        FileTree sum = tree.plus(other);
        assertThat(sum, instanceOf(CompositeFileTree.class));
        UnionFileTree sumCompositeTree = (UnionFileTree) sum;
        assertThat(sumCompositeTree.getSourceCollections(), equalTo((Iterable) toList(source1, source2, other)));
    }

    @Test
    public void visitsEachTreeWithVisitor() {
        final FileVisitor visitor = context.mock(FileVisitor.class);

        context.checking(new Expectations() {{
            oneOf(source1).visit(visitor);
            oneOf(source2).visit(visitor);
        }});

        tree.visit(visitor);
    }

    @Test
    public void visitsEachTreeWithClosure() {
        final Closure visitor = TestUtil.TEST_CLOSURE;
        final FileVisitor closureAsVisitor = DefaultGroovyMethods.asType(visitor, FileVisitor.class);

        context.checking(new Expectations() {{
            oneOf(source1).visit(closureAsVisitor);
            oneOf(source2).visit(closureAsVisitor);
        }});

        tree.visit(visitor);
    }

    @Test
    public void visitsEachTreeWithAction() {
        final Action<FileVisitDetails> visitor = Actions.doNothing();

        context.checking(new Expectations() {{
            oneOf(source1).visit(visitor);
            oneOf(source2).visit(visitor);
        }});

        tree.visit(visitor);
    }
}
