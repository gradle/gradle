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
package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DirectoryFileTreeTest {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private JUnit4Mockery context = new JUnit4GroovyMockery();
    private FileVisitor visitor;

    @Before
    public void setUp() {
        NativeServicesTestFixture.initialize();
        visitor = context.mock(FileVisitor.class);
    }

    @Test
    public void rootDirEmpty() throws IOException {
        final MockFile root = new MockFile(context, "root", false);
        root.setExpectations();

        DirectoryFileTree fileTree = new DirectoryFileTree(root.getMock());
        root.setExpectations();

        fileTree.visit(visitor);
    }

    @Test
    public void testUsesSpecFromPatternSetToMatchFilesAndDirs() {
        final PatternSet patternSet = context.mock(PatternSet.class);
        final Spec spec = context.mock(Spec.class);

        context.checking(new Expectations() {{
            one(patternSet).getAsSpec();
            will(returnValue(spec));
        }});

        DirectoryFileTree fileTree = new DirectoryFileTree(new File("root"), patternSet);
        fileTree.visit(visitor);
    }

    @Test
    public void walkSingleFile() throws IOException {

        final MockFile root = new MockFile(context, "root", false);
        final MockFile fileToCopy = root.addFile("file.txt");

        fileToCopy.setExpectations();

        context.checking(new Expectations() {{
            one(visitor).visitFile(with(file(fileToCopy)));
        }});

        DirectoryFileTree fileTree = new DirectoryFileTree(fileToCopy.getMock());
        fileTree.visit(visitor);
    }

    /*
    mock file structure:
    root
        rootFile1
        dir1
           dirFile1
           dirFile2
        rootFile2

        Test that the files are really walked breadth first
     */
    @Test
    public void walkBreadthFirst() throws IOException {
        final MockFile root = new MockFile(context, "root", false);
        final MockFile rootFile1 = root.addFile("rootFile1");
        final MockFile dir1 = root.addDir("dir1");
        final MockFile dirFile1 = dir1.addFile("dirFile1");
        final MockFile dirFile2 = dir1.addFile("dirFile2");
        final MockFile rootFile2 = root.addFile("rootFile2");
        root.setExpectations();

        final Sequence visiting = context.sequence("visiting");
        context.checking(new Expectations() {{
            one(visitor).visitFile(with(file(rootFile1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(rootFile2)));
            inSequence(visiting);
            one(visitor).visitDir(with(file(dir1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(dirFile1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(dirFile2)));
            inSequence(visiting);
        }});

        DirectoryFileTree fileTree = new DirectoryFileTree(root.getMock());
        fileTree.visit(visitor);
    }

    @Test
    public void walkDepthFirst() throws IOException {

        final MockFile root = new MockFile(context, "root", false);
        final MockFile rootFile1 = root.addFile("rootFile1");
        final MockFile dir1 = root.addDir("dir1");
        final MockFile dirFile1 = dir1.addFile("dirFile1");
        final MockFile dirFile2 = dir1.addFile("dirFile2");
        final MockFile rootFile2 = root.addFile("rootFile2");
        root.setExpectations();

        final Sequence visiting = context.sequence("visiting");
        context.checking(new Expectations() {{
            one(visitor).visitFile(with(file(rootFile1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(rootFile2)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(dirFile1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(dirFile2)));
            inSequence(visiting);
            one(visitor).visitDir(with(file(dir1)));
            inSequence(visiting);
        }});

        DirectoryFileTree fileTree = new DirectoryFileTree(root.getMock()).postfix();
        fileTree.visit(visitor);
    }

    @Test
    public void canApplyFilter() throws IOException {
        final MockFile root = new MockFile(context, "root", false);
        root.addFile("rootFile1");
        final MockFile dir1 = root.addDir("dir1");
        dir1.addFile("dirFile1");
        final MockFile dirFile2 = dir1.addFile("dirFile2");
        root.addFile("rootFile2");
        root.setExpectations();

        final Sequence visiting = context.sequence("visiting");
        context.checking(new Expectations() {{
            one(visitor).visitDir(with(file(dir1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(dirFile2)));
            inSequence(visiting);
        }});

        PatternSet patterns = new PatternSet();
        patterns.include("**/*2");
        PatternSet filter = new PatternSet();
        filter.include("dir1/**");
        DirectoryFileTree fileTree = new DirectoryFileTree(root.getMock(), patterns).filter(filter);
        fileTree.visit(visitor);
    }

    @Test
    public void canVisitorCanStopVisit() throws IOException {
        final MockFile root = new MockFile(context, "root", false);
        final MockFile rootFile1 = root.addFile("rootFile1");
        final MockFile dir1 = root.addDir("dir1");
        final MockFile dirFile1 = dir1.addFile("dirFile1");
        dir1.addFile("dirFile2");
        dir1.addDir("dir1Dir").addFile("dir1Dir1File1");
        final MockFile rootFile2 = root.addFile("rootFile2");
        root.setExpectations();

        context.checking(new Expectations() {{
            one(visitor).visitFile(with(file(rootFile1)));
            will(stopVisiting());
        }});

        DirectoryFileTree fileTree = new DirectoryFileTree(root.getMock());
        fileTree.visit(visitor);

        final Sequence visiting = context.sequence("visiting");
        context.checking(new Expectations() {{
            one(visitor).visitFile(with(file(rootFile1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(rootFile2)));
            inSequence(visiting);
            one(visitor).visitDir(with(file(dir1)));
            inSequence(visiting);
            one(visitor).visitFile(with(file(dirFile1)));
            will(stopVisiting());
            inSequence(visiting);
        }});

        fileTree.visit(visitor);
    }

    @Test
    public void canTestForFileMembership() {
        TestFile rootDir = tmpDir.createDir("root");
        TestFile rootTextFile = rootDir.file("a.txt").createFile();
        TestFile nestedTextFile = rootDir.file("a/b/c.txt").createFile();
        TestFile notTextFile = rootDir.file("a/b/c.html").createFile();
        TestFile excludedFile = rootDir.file("subdir1/a/b/c.html").createFile();
        TestFile notUnderRoot = tmpDir.createDir("root2").file("a.txt").createFile();
        TestFile doesNotExist = rootDir.file("b.txt");

        PatternSet patterns = new PatternSet();
        patterns.include("**/*.txt");
        patterns.exclude("subdir1/**");
        DirectoryFileTree fileTree = new DirectoryFileTree(rootDir, patterns);

        assertTrue(fileTree.contains(rootTextFile));
        assertTrue(fileTree.contains(nestedTextFile));
        assertFalse(fileTree.contains(notTextFile));
        assertFalse(fileTree.contains(excludedFile));
        assertFalse(fileTree.contains(notUnderRoot));
        assertFalse(fileTree.contains(doesNotExist));
    }

    @Test
    public void hasUsefulDisplayName() {
        DirectoryFileTree treeWithNoIncludesOrExcludes = new DirectoryFileTree(tmpDir.getTestDirectory());
        PatternSet includesOnly = new PatternSet();
        includesOnly.include("a/b", "c");
        DirectoryFileTree treeWithIncludes = new DirectoryFileTree(tmpDir.getTestDirectory(), includesOnly);
        PatternSet excludesOnly = new PatternSet();
        excludesOnly.exclude("a/b", "c");
        DirectoryFileTree treeWithExcludes = new DirectoryFileTree(tmpDir.getTestDirectory(), excludesOnly);

        assertThat(treeWithNoIncludesOrExcludes.getDisplayName(), equalTo(String.format("directory '%s'", tmpDir.getTestDirectory())));
        assertThat(treeWithIncludes.getDisplayName(), equalTo(String.format("directory '%s' include 'a/b', 'c'", tmpDir.getTestDirectory())));
        assertThat(treeWithExcludes.getDisplayName(), equalTo(String.format("directory '%s' exclude 'a/b', 'c'", tmpDir.getTestDirectory())));
    }

    private Action stopVisiting() {
        return new Action() {
            public void describeTo(Description description) {
                description.appendText("stop visiting");
            }

            public Object invoke(Invocation invocation) throws Throwable {
                FileVisitDetails details = (FileVisitDetails) invocation.getParameter(0);
                details.stopVisiting();
                return null;
            }
        };
    }

    // test excludes, includes

    private Matcher<FileVisitDetails> file(final MockFile file) {
        return new BaseMatcher<FileVisitDetails>() {
            public boolean matches(Object o) {
                FileVisitDetails details = (FileVisitDetails) o;
                return details.getFile().equals(file.getMock()) && details.getRelativePath().equals(file.getRelativePath());
            }

            public void describeTo(Description description) {
                description.appendText("details match file ").appendValue(file.getMock()).appendText(" with path ")
                        .appendValue(file.getRelativePath());
            }
        };
    }

    public class MockFile {
        private boolean isFile;
        private String name;
        private Mockery context;
        private List<MockFile> children;
        private File mock;
        private MockFile parent;

        public MockFile(Mockery context, String name, boolean isFile) {
            this.context = context;
            this.name = name;
            this.isFile = isFile;
            children = new ArrayList<MockFile>();
            mock = context.mock(File.class, name);
        }

        public File getMock() {
            return mock;
        }

        public MockFile addFile(String name) {
            MockFile child = new MockFile(context, name, true);
            child.setParent(this);
            children.add(child);
            return child;
        }

        public MockFile addDir(String name) {
            MockFile child = new MockFile(context, name, false);
            child.setParent(this);
            children.add(child);
            return child;
        }

        public void setParent(MockFile parent) {
            this.parent = parent;
        }

        public RelativePath getRelativePath() {
            if (parent == null) {
                return new RelativePath(isFile);
            } else {
                return parent.getRelativePath().append(isFile, name);
            }
        }

        public void setExpectations() {
            Expectations expectations = new Expectations();
            setExpectations(expectations);
            context.checking(expectations);
        }

        public void setExpectations(Expectations expectations) {
            try {
                expectations.allowing(mock).getCanonicalFile();
                expectations.will(expectations.returnValue(mock));
            } catch (IOException th) {
                // ignore
            }
            expectations.allowing(mock).isFile();
            expectations.will(expectations.returnValue(isFile));
            expectations.allowing(mock).getName();
            expectations.will(expectations.returnValue(name));
            expectations.allowing(mock).exists();
            expectations.will(expectations.returnValue(true));

            ArrayList<File> mockChildren = new ArrayList<File>(children.size());
            for (MockFile child : children) {
                mockChildren.add(child.getMock());
                child.setExpectations(expectations);
            }
            expectations.allowing(mock).listFiles();
            expectations.will(expectations.returnValue(mockChildren.toArray(new File[mockChildren.size()])));
        }
    }

}
