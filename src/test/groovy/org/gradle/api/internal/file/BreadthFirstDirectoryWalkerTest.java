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

import org.junit.Test;
import org.junit.Before;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import org.jmock.Mockery;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.gradle.util.HelperUtil;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

@RunWith(JMock.class)
public class BreadthFirstDirectoryWalkerTest {
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    private Project project;
    private CopyVisitor visitor;
    private CopyActionImpl copyAction;
    private BreadthFirstDirectoryWalker walker;

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject();
        visitor = context.mock(CopyVisitor.class);
        copyAction = context.mock(CopyActionImpl.class);
    }

    @Test public void rootDirEmpty() throws IOException {
        final MockFile root = new MockFile(context, "root", false);

        walker = new BreadthFirstDirectoryWalker(true, visitor);
        root.setExpectations();

        walker.start(root.getMock());
    }


    @Test public void testIsAllowedBothIncludeExclude() {
        walker = new BreadthFirstDirectoryWalker(true, visitor);

        walker.addIncludes(Arrays.asList(new String[]{ "*.java"}));
        walker.addExcludes(Arrays.asList(new String[]{ "*Test.java"}));

        assertTrue(walker.isAllowed(new RelativePath(true, "Fred.java")));
        assertFalse(walker.isAllowed(new RelativePath(true, "Fred")));
        assertFalse(walker.isAllowed(new RelativePath(true, "FredTest.java")));
    }

    @Test public void testIsAllowedOnlyInclude() {
        walker = new BreadthFirstDirectoryWalker(true, visitor);

        walker.addIncludes(Arrays.asList(new String[]{ "*.java"}));

        assertTrue(walker.isAllowed(new RelativePath(true, "Fred.java")));
        assertFalse(walker.isAllowed(new RelativePath(true, "Fred")));
        assertTrue(walker.isAllowed(new RelativePath(true, "FredTest.java")));
    }

    @Test public void testIsAllowedOnlyExclude() {
        walker = new BreadthFirstDirectoryWalker(true, visitor);

        walker.addExcludes(Arrays.asList(new String[]{ "*.bak"}));

        assertTrue(walker.isAllowed(new RelativePath(true, "Fred.java")));
        assertFalse(walker.isAllowed(new RelativePath(true, "Fred.bak")));
    }

    @Test public void testIsAllowedNoIncludeExclude() {
        walker = new BreadthFirstDirectoryWalker(true, visitor);

        assertTrue(walker.isAllowed(new RelativePath(true, "Fred.java")));
        assertTrue(walker.isAllowed(new RelativePath(true, "Fred.bak")));
    }

    @Test public void walkSingleFile() throws IOException {
        walker = new BreadthFirstDirectoryWalker(true, visitor);

        final MockFile root = new MockFile(context, "root", false);
        final MockFile fileToCopy = root.addFile("file.txt");

        fileToCopy.setExpectations();

        context.checking(new Expectations() {{
            one(visitor).visitFile(fileToCopy.getMock(), fileToCopy.getRelativePath());
        }});

        walker.start(fileToCopy.getMock());
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
    @Test public void walkBreadthFirst() throws IOException {

        walker = new BreadthFirstDirectoryWalker(true, visitor);

        final MockFile root = new MockFile(context, "root", false);
        final MockFile rootFile1 = root.addFile("rootFile1");
        final MockFile dir1 = root.addDir("dir1");
        final MockFile dirFile1 = dir1.addFile("dirFile1");
        final MockFile dirFile2 = dir1.addFile("dirFile2");
        final MockFile rootFile2 = root.addFile("rootFile2");
        root.setExpectations();

        final Sequence visiting = context.sequence("visiting");
        context.checking(new Expectations() {{
            one(visitor).visitFile(rootFile1.getMock(), rootFile1.getRelativePath()); inSequence(visiting);
            one(visitor).visitFile(rootFile2.getMock(), rootFile2.getRelativePath()); inSequence(visiting);
            one(visitor).visitDir(dir1.getMock(), dir1.getRelativePath());            inSequence(visiting);
            one(visitor).visitFile(dirFile1.getMock(), dirFile1.getRelativePath());   inSequence(visiting);
            one(visitor).visitFile(dirFile2.getMock(), dirFile2.getRelativePath());   inSequence(visiting);
        }});

        walker.start(root.getMock());
    }

    // test excludes, includes

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
                return new RelativePath(isFile, parent.getRelativePath(), name);
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
            } catch (Throwable th){};
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
