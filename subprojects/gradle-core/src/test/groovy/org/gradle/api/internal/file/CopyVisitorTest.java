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
import org.apache.tools.ant.filters.ReplaceTokens;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.util.TemporaryFolder;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;


public class CopyVisitorTest {
    private File testDir;
    private File sourceDir;
    private CopyVisitor visitor;
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();


    @Before public void setUp() throws IOException {
        testDir = tmpDir.getDir();
        sourceDir = getResource("testfiles");
        assertTrue(sourceDir.isDirectory());
    }

    private File getResource(String path) {
        URL resource = getClass().getResource(path);
        assertThat(String.format("Could not find resource '%s'", path), resource, notNullValue());
        assertThat(resource.getProtocol(), equalTo("file"));
        File result;
        try {
            result = new File(resource.toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(String.format("Could not locate test file '%s'.", path));
        }
        return result;
    }

    @Test public void plainCopy() {
        visitor = new CopyVisitor(testDir, null, null, new FilterChain());

        visitor.visitDir(file(sourceDir, new RelativePath(false)));

        File rootFile = getResource("testfiles/rootfile.txt");
        File subDir = getResource("testfiles/subdir");
        File anotherFile = getResource("testfiles/subdir/anotherfile.txt");

        visitor.visitFile(file(rootFile, new RelativePath(true, rootFile.getName())));

        RelativePath subDirPath = new RelativePath(false, subDir.getName());
        visitor.visitDir(file(subDir, subDirPath));
        visitor.visitFile(file(anotherFile, new RelativePath(true, subDirPath, anotherFile.getName())));

        File targetRootFile = new File(testDir, rootFile.getName());
        assertTrue(targetRootFile.exists());

        File targetAnotherFile = new File(testDir, "subdir/"+anotherFile.getName());
        assertTrue(targetAnotherFile.exists());
    }

    @Test public void testFilter() throws IOException {
        FilterChain filters = new FilterChain();
        ReplaceTokens filter = new ReplaceTokens(filters.getLastFilter());
        filters.addFilter(filter);
        ReplaceTokens.Token token = new ReplaceTokens.Token();
        token.setKey("MAGIC");
        token.setValue("42");
        filter.addConfiguredToken(token);

        visitor = new CopyVisitor(testDir, null, null, filters);

        FileVisitDetails sourceFile = file(getResource("testfiles/rootfile.txt"), new RelativePath(true,
                "rootfile.txt"));
        File destFile = new File(testDir, "rootfile.txt");

        visitor.copyFile(sourceFile, destFile);

        assertTrue(destFile.exists());
        BufferedReader reader = new BufferedReader(new FileReader(destFile));
        assertTrue(reader.readLine().startsWith("The magic number is 42"));
    }

    @Test public void testGetTargetPlain() {
        visitor = new CopyVisitor(testDir, null, null, new FilterChain());

        File target = visitor.getTarget(new RelativePath(true, "one"));
        assertEquals(new File(testDir, "one"), target);

        target = visitor.getTarget(new RelativePath(true, "sub", "two"));
        assertEquals(new File(testDir, "sub/two"), target);
    }

    @Test public void testGetTargetRenamed() {
        RegExpNameMapper renamer = new RegExpNameMapper("(.+)\\.java", "$1Test.java");
        ArrayList<Transformer<String>> mappers = new ArrayList<Transformer<String>>(1);
        mappers.add(renamer);

        visitor = new CopyVisitor(testDir, null, mappers, new FilterChain());

        File target = visitor.getTarget(new RelativePath(true, "Fred.java"));
        assertEquals(new File(testDir, "FredTest.java"), target);
    }

    @Test public void testGetTargetRenmapped() {

        ArrayList<Closure> mappers = new ArrayList<Closure>(1);
        mappers.add(new FlatMapper(this, testDir));

        visitor = new CopyVisitor(testDir, mappers, null, new FilterChain());

        File target = visitor.getTarget(new RelativePath(true, "Fred.java"));
        assertEquals(new File(testDir, "Fred.java"), target);
    }

    private FileVisitDetails file(final File sourceDir, final RelativePath relativePath) {
        return new TestFileVisitDetails(sourceDir, relativePath);
    }


    public class FlatMapper extends Closure {
        private File dir;

        public FlatMapper(Object owner, File dir) {
            super(owner);
            this.dir = dir;
        }

        public Object call(Object[] args) {
            File target = (File) args[0];
            return new File(dir, target.getName());
        }
    }

    private class TestFileVisitDetails extends DefaultFileTreeElement implements FileVisitDetails {
        public TestFileVisitDetails(File sourceDir, RelativePath relativePath) {
            super(sourceDir, relativePath);
        }

        public void stopVisiting() {
            throw new UnsupportedOperationException();
        }
    }
}
