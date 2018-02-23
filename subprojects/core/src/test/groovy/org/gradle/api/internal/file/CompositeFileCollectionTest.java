/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class CompositeFileCollectionTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final AbstractFileCollection source1 = context.mock(AbstractFileCollection.class, "source1");
    private final AbstractFileCollection source2 = context.mock(AbstractFileCollection.class, "source2");
    private final TestCompositeFileCollection collection = new TestCompositeFileCollection(source1, source2);

    @Before
    public void setUp() {
        NativeServicesTestFixture.initialize();
    }

    @Test
    public void containsUnionOfAllSourceCollections() {
        final File file1 = new File("1");
        final File file2 = new File("2");
        final File file3 = new File("3");

        context.checking(new Expectations() {{
            oneOf(source1).getFiles();
            will(returnValue(toSet(file1, file2)));
            oneOf(source2).getFiles();
            will(returnValue(toSet(file2, file3)));
        }});

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

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2, file3)));
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file3)));
    }

    @Test
    public void containsFileWhenAtLeastOneSourceCollectionContainsFile() {
        final File file1 = new File("1");

        context.checking(new Expectations() {{
            oneOf(source1).contains(file1);
            will(returnValue(false));
            oneOf(source2).contains(file1);
            will(returnValue(true));
        }});

        assertTrue(collection.contains(file1));
    }

    @Test
    public void doesNotContainFileWhenNoSourceCollectionsContainFile() {
        final File file1 = new File("1");

        context.checking(new Expectations() {{
            oneOf(source1).contains(file1);
            will(returnValue(false));
            oneOf(source2).contains(file1);
            will(returnValue(false));
        }});

        assertFalse(collection.contains(file1));
    }

    @Test
    public void isEmptyWhenHasNoSets() {
        CompositeFileCollection set = new TestCompositeFileCollection();
        assertTrue(set.isEmpty());
    }

    @Test
    public void isEmptyWhenAllSetsAreEmpty() {
        context.checking(new Expectations() {{
            oneOf(source1).isEmpty();
            will(returnValue(true));
            oneOf(source2).isEmpty();
            will(returnValue(true));
        }});

        assertTrue(collection.isEmpty());
    }

    @Test
    public void isNotEmptyWhenAnySetIsNotEmpty() {
        context.checking(new Expectations() {{
            oneOf(source1).isEmpty();
            will(returnValue(false));
        }});

        assertFalse(collection.isEmpty());
    }

    @Test
    public void addToAntBuilderDelegatesToEachSet() {
        context.checking(new Expectations() {{
            oneOf(source1).addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection);
            oneOf(source2).addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection);
        }});

        collection.addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection);
    }

    @Test
    public void getAsFileTreesReturnsUnionOfFileTrees() {
        final DirectoryFileTreeFactory directoryFileTreeFactory = new DefaultDirectoryFileTreeFactory();
        final DirectoryFileTree set1 = directoryFileTreeFactory.create(new File("dir1").getAbsoluteFile());
        final DirectoryFileTree set2 = directoryFileTreeFactory.create(new File("dir2").getAbsoluteFile());

        context.checking(new Expectations() {{
            oneOf(source1).getAsFileTrees();
            will(returnValue(toList((Object) set1)));
            oneOf(source2).getAsFileTrees();
            will(returnValue(toList((Object) set2)));
        }});
        assertThat(collection.getAsFileTrees(), equalTo((Collection) toList(set1, set2)));
    }

    @Test
    public void getAsFileTreeDelegatesToEachSet() {
        final File file1 = new File("dir1");
        final File file2 = new File("dir2");

        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));

        context.checking(new Expectations() {{
            oneOf(source1).getFiles();
            will(returnValue(toSet(file1)));
            oneOf(source2).getFiles();
            will(returnValue(toSet(file2)));
        }});

        ((CompositeFileTree) fileTree).getSourceCollections();
    }

    @Test
    public void fileTreeIsLive() {
        final File dir1 = new File("dir1");
        final File dir2 = new File("dir1");
        final File dir3 = new File("dir1");
        final MinimalFileSet source3 = context.mock(MinimalFileSet.class);

        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));

        context.checking(new Expectations() {{
            oneOf(source1).getFiles();
            will(returnValue(toSet(dir1)));
            oneOf(source2).getFiles();
            will(returnValue(toSet(dir2)));
        }});

        ((CompositeFileTree) fileTree).getSourceCollections();

        collection.sourceCollections.add(source3);

        context.checking(new Expectations() {{
            oneOf(source1).getFiles();
            will(returnValue(toSet(dir1)));
            oneOf(source2).getFiles();
            will(returnValue(toSet(dir2)));
            oneOf(source3).getFiles();
            will(returnValue(toSet(dir3)));
        }});

        ((CompositeFileTree) fileTree).getSourceCollections();
    }

    @Test
    public void filterDelegatesToEachSet() {
        final FileCollectionInternal filtered1 = context.mock(FileCollectionInternal.class);
        final FileCollectionInternal filtered2 = context.mock(FileCollectionInternal.class);
        @SuppressWarnings("unchecked")
        final Spec<File> spec = context.mock(Spec.class);

        FileCollection filtered = collection.filter(spec);
        assertThat(filtered, instanceOf(CompositeFileCollection.class));

        context.checking(new Expectations() {{
            oneOf(source1).filter(spec);
            will(returnValue(filtered1));
            oneOf(source2).filter(spec);
            will(returnValue(filtered2));
        }});

        assertThat(((CompositeFileCollection) filtered).getSourceCollections(), equalTo((Iterable) toList(filtered1, filtered2)));
    }

    private class TestCompositeFileCollection extends CompositeFileCollection {
        private List<Object> sourceCollections;

        public TestCompositeFileCollection(FileCollection... sourceCollections) {
            this.sourceCollections = new ArrayList<Object>(Arrays.asList(sourceCollections));
        }

        @Override
        public String getDisplayName() {
            return "<display name>";
        }

        @Override
        public void visitContents(FileCollectionResolveContext context) {
            context.add(sourceCollections);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            throw new UnsupportedOperationException();
        }
    }
}
