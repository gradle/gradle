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
package org.gradle.integtests;

import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.util.GFileUtils;
import org.gradle.util.CompressUtil;
import org.gradle.util.GradleUtil;
import static org.junit.Assert.*;
import org.hamcrest.Matcher;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.net.URI;
import java.net.URL;
import java.net.URISyntaxException;

import groovy.lang.Closure;

public class TestFile extends File {
    public TestFile(File file, Object... path) {
        super(join(file, path).getAbsolutePath());
    }

    public TestFile(URI uri) {
        this(new File(uri));
    }

    public TestFile(URL url) {
        this(toUri(url));
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static File join(File file, Object[] path) {
        File current = GFileUtils.canonicalise(file);
        for (Object p : path) {
            current = GFileUtils.canonicalise(new File(current, p.toString()));
        }
        return current;
    }

    public TestFile file(Object... path) {
        return new TestFile(this, path);
    }

    public List<TestFile> files(Object... paths) {
        List<TestFile> files = new ArrayList<TestFile>();
        for (Object path : paths) {
            files.add(file(path));
        }
        return files;
    }

    public TestFile writelns(String... lines) {
        return writelns(Arrays.asList(lines));
    }

    public TestFile write(Object content) {
        try {
            FileUtils.writeStringToFile(this, content.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not write to test file '%s'", this), e);
        }
        return this;
    }

    public TestFile leftShift(Object content) {
        return write(content);
    }

    public String getText() {
        assertIsFile();
        try {
            return FileUtils.readFileToString(this);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not read from test file '%s'", this), e);
        }
    }

    public void unzipTo(File target) {
        assertIsFile();
        CompressUtil.unzip(this, target);
    }

    public void copyTo(File target) {
        try {
            FileUtils.copyFile(this, target);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not copy test file '%s' to '%s'", this, target), e);
        }
    }

    public TestFile touch() {
        try {
            FileUtils.touch(this);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertIsFile();
        return this;
    }

    /**
     * Creates a directory structure specified by the given closure.
     * <pre>
     * dir.create {
     *     subdir1 {
     *        file 'somefile.txt'
     *     }
     *     subdir2 { nested { file 'someFile' } }
     * }
     * </pre>
     */
    public void create(Closure structure) {
        assertTrue(isDirectory() || mkdirs());
        new TestDirHelper(this).apply(structure);
    }

    @Override
    public String toString() {
        return getPath();
    }

    public TestFile writelns(Iterable<String> lines) {
        Formatter formatter = new Formatter();
        for (String line : lines) {
            formatter.format("%s%n", line);
        }
        return write(formatter);
    }

    public void assertExists() {
        assertTrue(String.format("%s does not exist", this), exists());
    }

    public void assertIsFile() {
        assertTrue(String.format("%s is not a file", this), isFile());
    }

    public void assertIsDir() {
        assertTrue(String.format("%s is not a directory", this), isDirectory());
    }

    public void assertDoesNotExist() {
        assertFalse(String.format("%s should not exist", this), exists());
    }

    public void assertContents(Matcher<String> matcher) {
        assertThat(getText(), matcher);
    }

    /**
     * Asserts that this file contains exactly the given set of descendants.
     */
    public void assertHasDescendants(String... descendants) {
        Set<String> actual = new TreeSet<String>();
        assertTrue(this.isDirectory());
        visit(actual, "", this);
        Set<String> expected = new TreeSet<String>(Arrays.asList(descendants));
        assertEquals(expected, actual);
    }

    private void visit(Set<String> names, String prefix, File file) {
        for (File child : file.listFiles()) {
            if (child.isFile()) {
                names.add(prefix + child.getName());
            } else if (child.isDirectory()) {
                visit(names, prefix + child.getName() + "/", child);
            }
        }
    }

    public boolean isSelfOrDescendent(File file) {
        if (file.getAbsolutePath().equals(getAbsolutePath())) {
            return true;
        }
        return file.getAbsolutePath().startsWith(getAbsolutePath() + File.separatorChar);
    }

    public TestFile createDir() {
        assertTrue(isDirectory() || mkdirs());
        return this;
    }

    public TestFile deleteDir() {
        GradleUtil.deleteDir(this);
        return this;
    }
}
