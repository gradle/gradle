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
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.util.FileSet;
import org.gradle.util.GFileUtils;
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
    private final File testDir = HelperUtil.makeNewTestDir();
    private final FileResolver resolverMock = context.mock(FileResolver.class);

    @Test
    public void resolvesSpecifiedFilesAgainstProject() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        FileCollection collection = new PathResolvingFileCollection(resolverMock, "src1", "src2");

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
        FileCollection collection = new PathResolvingFileCollection(resolverMock, closure);

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        files.add('b');

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseAClosureToSpecifyASingleFile() {
        Closure closure = HelperUtil.returns('a');
        final File file = new File("1");

        FileCollection collection = new PathResolvingFileCollection(resolverMock, closure);

        context.checking(new Expectations() {{
            one(resolverMock).resolve('a');
            will(returnValue(file));
        }});

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file)));
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
        FileCollection collection = new PathResolvingFileCollection(resolverMock, files);

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1)));

        files.add("src2");

        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }

    @Test
    public void canUseNestedClosuresAndCollectionsToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        context.checking(new Expectations() {{
            allowing(resolverMock).resolve("src1");
            will(returnValue(file1));
            allowing(resolverMock).resolve("src2");
            will(returnValue(file2));
        }});

        FileCollection collection = new PathResolvingFileCollection(resolverMock, HelperUtil.toClosure("{[{['src1', { 'src2' }]}]}"));
        assertThat(collection.getFiles(), equalTo(toLinkedSet(file1, file2)));
    }
    
    @Test
    public void canUseAFileCollectionToSpecifyTheContentsOfTheCollection() {
        final File file1 = new File("1");
        final File file2 = new File("2");

        final FileCollection src = context.mock(FileCollection.class);

        FileCollection collection = new PathResolvingFileCollection(resolverMock, toList((Object) src));

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
    public void convertsEachFileToFlatFileTree() {
        final File file = new File(testDir, "f");
        GFileUtils.touch(file);

        context.checking(new Expectations(){{
            one(resolverMock).resolve("file");
            will(returnValue(file));
        }});

        FileCollection collection = new PathResolvingFileCollection(resolverMock, toList("file"));
        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));
        assertThat(((CompositeFileTree) fileTree).getSourceCollections().iterator().next(), instanceOf(
                FlatFileTree.class));
    }

    @Test
    public void convertsEachDirectoryToFileSet() {
        context.checking(new Expectations(){{
            one(resolverMock).resolve("dir");
            will(returnValue(testDir));
            allowing(resolverMock).resolve(testDir);
            will(returnValue(testDir));
        }});

        FileCollection collection = new PathResolvingFileCollection(resolverMock, toList("dir"));
        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));
        assertThat(((CompositeFileTree) fileTree).getSourceCollections().iterator().next(), instanceOf(
                FileSet.class));
    }
    
    @Test
    public void convertsEachFileCollectionToFileTree() {
        final FileCollection fileCollectionMock = context.mock(FileCollection.class);
        final FileTree fileTreeDummy = context.mock(FileTree.class);

        FileCollection collection = new PathResolvingFileCollection(resolverMock, toList((Object) fileCollectionMock));
        context.checking(new Expectations(){{
            one(fileCollectionMock).getAsFileTree();
            will(returnValue(fileTreeDummy));
        }});
        FileTree fileTree = collection.getAsFileTree();
        assertThat(fileTree, instanceOf(CompositeFileTree.class));
        assertThat(((CompositeFileTree) fileTree).getSourceCollections().iterator().next(), sameInstance(fileTreeDummy));
    }
}
