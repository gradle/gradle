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

import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileTree;
import org.gradle.api.internal.file.collections.FileCollectionResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskDependency;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class CompositeFileCollectionTest {
    private final JUnit4Mockery context = new JUnit4Mockery(){{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private final AbstractFileCollection source1 = context.mock(AbstractFileCollection.class, "source1");
    private final AbstractFileCollection source2 = context.mock(AbstractFileCollection.class, "source2");
    private final TestCompositeFileCollection collection = new TestCompositeFileCollection(source1, source2);

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
            one(source1).contains(file1);
            will(returnValue(false));
            one(source2).contains(file1);
            will(returnValue(true));
        }});

        assertTrue(collection.contains(file1));
    }

    @Test
    public void doesNotContainFileWhenNoSourceCollectionsContainFile() {
        final File file1 = new File("1");

        context.checking(new Expectations() {{
            one(source1).contains(file1);
            will(returnValue(false));
            one(source2).contains(file1);
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
            one(source1).isEmpty();
            will(returnValue(true));
            one(source2).isEmpty();
            will(returnValue(true));
        }});

        assertTrue(collection.isEmpty());
    }

    @Test
    public void isNotEmptyWhenAnySetIsNotEmpty() {
        context.checking(new Expectations() {{
            one(source1).isEmpty();
            will(returnValue(false));
        }});

        assertFalse(collection.isEmpty());
    }

    @Test
    public void addToAntBuilderDelegatesToEachSet() {
        context.checking(new Expectations() {{
            one(source1).addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection);
            one(source2).addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection);
        }});

        collection.addToAntBuilder("node", "name", FileCollection.AntType.ResourceCollection);
    }

    @Test
    public void getAsFileSetsReturnsUnionOfFileSets() {
        final DefaultConfigurableFileTree set1 = new DefaultConfigurableFileTree(new File("dir1").getAbsoluteFile(), null, null);
        final DefaultConfigurableFileTree set2 = new DefaultConfigurableFileTree(new File("dir2").getAbsoluteFile(), null, null);

        context.checking(new Expectations() {{
            one(source1).getAsFileTrees();
            will(returnValue(toList((Object) set1)));
            one(source2).getAsFileTrees();
            will(returnValue(toList((Object) set2)));
        }});
        assertThat(collection.getAsFileTrees(), equalTo((Collection) toList(set1, set2)));
    }
    
    @Test
    public void getAsFileTreeDelegatesToEachSet() {
        final FileTree tree1 = context.mock(FileTree.class, "tree1");
        final FileTree tree2 = context.mock(FileTree.class, "tree2");

        context.checking(new Expectations() {{
            one(source1).getAsFileTree();
            will(returnValue(tree1));
            one(source2).getAsFileTree();
            will(returnValue(tree2));
        }});

        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));
        assertThat(((CompositeFileTree) fileTree).getSourceCollections(), equalTo((Iterable) toList(tree1, tree2)));
    }

    @Test
    public void fileTreeIsLive() {
        final FileTree tree1 = context.mock(FileTree.class, "tree1");
        final FileTree tree2 = context.mock(FileTree.class, "tree2");
        final FileCollection source3 = context.mock(FileCollection.class);
        final FileTree tree3 = context.mock(FileTree.class);

        context.checking(new Expectations() {{
            one(source1).getAsFileTree();
            will(returnValue(tree1));
            one(source2).getAsFileTree();
            will(returnValue(tree2));
        }});

        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));
        assertThat(((CompositeFileTree) fileTree).getSourceCollections(), equalTo((Iterable) toList(tree1, tree2)));

        collection.sourceCollections.add(source3);

        context.checking(new Expectations() {{
            one(source1).getAsFileTree();
            will(returnValue(tree1));
            one(source2).getAsFileTree();
            will(returnValue(tree2));
            one(source3).getAsFileTree();
            will(returnValue(tree3));
        }});

        assertThat(((CompositeFileTree) fileTree).getSourceCollections(), equalTo((Iterable) toList(tree1, tree2, tree3)));
    }

    @Test
    public void filterDelegatesToEachSet() {
        final FileCollection filtered1 = context.mock(FileCollection.class, "filtered1");
        final FileCollection filtered2 = context.mock(FileCollection.class, "filtered2");
        final Spec spec = context.mock(Spec.class);

        context.checking(new Expectations() {{
            one(source1).filter(spec);
            will(returnValue(filtered1));
            one(source2).filter(spec);
            will(returnValue(filtered2));
        }});

        FileCollection filtered = collection.filter(spec);
        assertThat(filtered, instanceOf(CompositeFileCollection.class));
        assertThat(((CompositeFileCollection) filtered).getSourceCollections(), equalTo((Iterable) toList(filtered1, filtered2)));
    }

    @Test
    public void dependsOnLiveUnionOfAllDependencies() {
        final Task target = context.mock(Task.class, "target");
        final Task task1 = context.mock(Task.class, "task1");
        final Task task2 = context.mock(Task.class, "task2");
        final Task task3 = context.mock(Task.class, "task3");
        final FileCollection source3 = context.mock(FileCollection.class, "source3");

        context.checking(new Expectations(){{
            TaskDependency dependency1 = context.mock(TaskDependency.class, "dep1");
            TaskDependency dependency2 = context.mock(TaskDependency.class, "dep2");
            TaskDependency dependency3 = context.mock(TaskDependency.class, "dep3");

            allowing(source1).getBuildDependencies();
            will(returnValue(dependency1));
            allowing(dependency1).getDependencies(target);
            will(returnValue(toSet(task1)));
            allowing(source2).getBuildDependencies();
            will(returnValue(dependency2));
            allowing(dependency2).getDependencies(target);
            will(returnValue(toSet(task2)));
            allowing(source3).getBuildDependencies();
            will(returnValue(dependency3));
            allowing(dependency3).getDependencies(target);
            will(returnValue(toSet(task3)));
        }});

        TaskDependency dependency = collection.getBuildDependencies();
        assertThat(dependency.getDependencies(target), equalTo((Set) toSet(task1, task2)));

        collection.sourceCollections.add(source3);

        assertThat(dependency.getDependencies(target), equalTo((Set) toSet(task1, task2, task3)));
    }

    private class TestCompositeFileCollection extends CompositeFileCollection {
        private List<FileCollection> sourceCollections;

        public TestCompositeFileCollection(FileCollection... sourceCollections) {
            this.sourceCollections = new ArrayList<FileCollection>(Arrays.asList(sourceCollections));
        }

        @Override
        public String getDisplayName() {
            return "<display name>";
        }

        @Override
        protected void resolve(FileCollectionResolveContext context) {
            context.add(sourceCollections);
        }
    }
}
