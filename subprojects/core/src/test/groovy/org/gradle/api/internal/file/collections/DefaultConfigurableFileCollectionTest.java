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
package org.gradle.api.internal.file.collections;

import groovy.lang.Closure;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.HelperUtil;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.Matchers.isEmpty;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class DefaultConfigurableFileCollectionTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final FileResolver resolverMock = context.mock(FileResolver.class);
    private final TaskResolver taskResolverStub = context.mock(TaskResolver.class);
    private final DefaultConfigurableFileCollection collection = new DefaultConfigurableFileCollection(resolverMock,
            taskResolverStub);

    @Test
    public void resolvesSpecifiedFilesUseFileResolver() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        DefaultConfigurableFileCollection collection = new DefaultConfigurableFileCollection(resolverMock, taskResolverStub, "a", "b");

        context.checking(new Expectations() {{
            one(resolverMock).resolve("a");
            will(returnValue(file1));
            one(resolverMock).resolve("b");
            will(returnValue(file2));
        }});

        assertThat(collection.getFrom(), equalTo(toLinkedSet((Object) "a", "b")));
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canAddPathsToTheCollection() {
        collection.from("src1", "src2");
        assertThat(collection.getFrom(), equalTo(toLinkedSet((Object) "src1", "src2")));
    }

    @Test
    public void canSetThePathsOfTheCollection() {
        collection.from("ignore-me");

        collection.setFrom("src1", "src2");
        assertThat(collection.getFrom(), equalTo(toLinkedSet((Object) "src1", "src2")));

        collection.setFrom(toList("a", "b"));
        assertThat(collection.getFrom(), equalTo(toLinkedSet((Object) toList("a", "b"))));
    }

    @Test
    public void resolvesSpecifiedPathsUseFileResolver() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        DefaultConfigurableFileCollection collection = new DefaultConfigurableFileCollection(resolverMock, taskResolverStub, "src1",
                "src2");

        context.checking(new Expectations() {{
            one(resolverMock).resolve("src1");
            will(returnValue(file1));
            one(resolverMock).resolve("src2");
            will(returnValue(file2));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolverMock).resolve('a');
            will(returnValue(file1));
            allowing(resolverMock).resolve('b');
            will(returnValue(file2));
        }});

        List<Character> files = toList('a');
        Closure closure = HelperUtil.returns(files);
        collection.from(closure);

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        files.add('b');

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyASingleFile() {
        Closure closure = HelperUtil.returns('a');
        final File file = new File("1");

        collection.from(closure);

        context.checking(new Expectations() {{
            one(resolverMock).resolve('a');
            will(returnValue(file));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file)));
    }

    @Test
    public void closureCanReturnNull() {
        Closure closure = HelperUtil.returns(null);

        collection.from(closure);

        assertThat(collection.getFiles(), isEmpty());
    }

    @Test
    public void canUseACollectionToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolverMock).resolve("src1");
            will(returnValue(file1));
            allowing(resolverMock).resolve("src2");
            will(returnValue(file2));
        }});

        List<String> files = toList("src1");
        collection.from(files);

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        files.add("src2");

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAnArrayToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolverMock).resolve("src1");
            will(returnValue(file1));
            allowing(resolverMock).resolve("src2");
            will(returnValue(file2));
        }});

        collection.from((Object) toArray("src1", "src2"));
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseNestedObjectsToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolverMock).resolve("src1");
            will(returnValue(file1));
            allowing(resolverMock).resolve("src2");
            will(returnValue(file2));
        }});

        collection.from(HelperUtil.toClosure("{[{['src1', { ['src2'] as String[] }]}]}"));
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAFileCollectionToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        final FileCollection src = context.mock(FileCollection.class);

        collection.from(src);

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

    @Test
    public void canUseACallableToSpecifyTheContentsOfTheCollection() throws Exception {
        final File file1 = new File("1");
        final File file2 = new File("2");
        final Callable callable = context.mock(Callable.class);

        context.checking(new Expectations() {{
            one(callable).call();
            will(returnValue(toList("src1", "src2")));
            allowing(resolverMock).resolve("src1");
            will(returnValue(file1));
            allowing(resolverMock).resolve("src2");
            will(returnValue(file2));
        }});

        collection.from(callable);
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void callableCanReturnNull() throws Exception {
        final Callable callable = context.mock(Callable.class);

        context.checking(new Expectations() {{
            one(callable).call();
            will(returnValue(null));
        }});

        collection.from(callable);
        assertThat(collection.getFiles(), isEmpty());
    }

    @Test
    public void resolveAddsEachSourceObjectAndBuildDependencies() {
        final FileCollectionResolveContext resolveContext = context.mock(FileCollectionResolveContext.class);
        final FileCollectionResolveContext nestedContext = context.mock(FileCollectionResolveContext.class);
        final FileCollection fileCollectionMock = context.mock(FileCollection.class);

        collection.from("file");
        collection.from(fileCollectionMock);

        context.checking(new Expectations() {{
            one(resolveContext).push(resolverMock);
            will(returnValue(nestedContext));
            one(nestedContext).add(collection.getFrom());
        }});

        collection.resolve(resolveContext);
    }

    @Test
    public void resolveBuildDependenciesWhenNoEmpty() {
        final FileCollectionResolveContext resolveContext = context.mock(FileCollectionResolveContext.class);
        final FileCollectionResolveContext nestedContext = context.mock(FileCollectionResolveContext.class);
        final FileCollection fileCollectionMock = context.mock(FileCollection.class);

        collection.from("file");
        collection.builtBy("classes");
        collection.from(fileCollectionMock);

        context.checking(new Expectations() {{
            one(resolveContext).push(resolverMock);
            will(returnValue(nestedContext));
            one(nestedContext).add(with(notNullValue(TaskDependency.class)));
            one(nestedContext).add(collection.getFrom());
        }});

        collection.resolve(resolveContext);
    }

    @Test
    public void canGetAndSetTaskDependencies() {
        assertThat(collection.getBuiltBy(), isEmpty());

        collection.builtBy("a");
        collection.builtBy("b");
        collection.from("f");

        assertThat(collection.getBuiltBy(), equalTo(toSet((Object) "a", "b")));

        collection.setBuiltBy(toList("c"));

        assertThat(collection.getBuiltBy(), equalTo(toSet((Object) "c")));

        final Task task = context.mock(Task.class);
        context.checking(new Expectations() {{
            allowing(resolverMock).resolve("f");
            will(returnValue(new File("f")));
            allowing(taskResolverStub).resolveTask("c");
            will(returnValue(task));
        }});

        assertThat(collection.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(task)));
    }

    @Test
    public void taskDependenciesContainsUnionOfDependenciesOfNestedFileCollectionsPlusOwnDependencies() {
        final FileCollection fileCollectionMock = context.mock(FileCollection.class);

        collection.from(fileCollectionMock);
        collection.from("f");
        collection.builtBy('b');

        final Task taskA = context.mock(Task.class, "a");
        final Task taskB = context.mock(Task.class, "b");
        context.checking(new Expectations() {{
            allowing(resolverMock).resolve("f");
            will(returnValue(new File("f")));
            TaskDependency dependency = context.mock(TaskDependency.class);
            allowing(fileCollectionMock).getBuildDependencies();
            will(returnValue(dependency));
            allowing(dependency).getDependencies(null);
            will(returnValue(toSet(taskA)));
            allowing(taskResolverStub).resolveTask('b');
            will(returnValue(taskB));
        }});

        assertThat(collection.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(taskA, taskB)));
    }

    @Test
    public void hasSpecifiedDependenciesWhenEmpty() {
        collection.builtBy("task");

        final Task task = context.mock(Task.class);
        context.checking(new Expectations(){{
            allowing(taskResolverStub).resolveTask("task");
            will(returnValue(task));
        }});

        assertThat(collection.getBuildDependencies().getDependencies(null), equalTo((Set) toSet(task)));
        assertThat(collection.getAsFileTree().getBuildDependencies().getDependencies(null), equalTo((Set) toSet(task)));
        assertThat(collection.getAsFileTree().matching(HelperUtil.TEST_CLOSURE).getBuildDependencies().getDependencies(null), equalTo((Set) toSet(task)));
    }
    
}
