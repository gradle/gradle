/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.testkit.runner.fixtures.file;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Incubating;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

/**
 * Represents a file used by a test. Extends from {@link java.io.File} and introduces additional convenience methods.
 *
 * @since 4.2
 */
@Incubating
public class TestFile extends File {

    public TestFile(String pathname) {
        super(pathname);
    }

    public TestFile(String parent, String child) {
        super(parent, child);
    }

    public TestFile(File parent, String child) {
        super(parent, child);
    }

    public TestFile(URI uri) {
        super(uri);
    }

    public TestFile(File file, Object... path) {
        super(join(file, path).getAbsolutePath());
    }

    /**
     * Returns a new file handle for given path. Does not automatically create it. To create a new directory or file for path use the methods {@see #createDirectory()} or {@see #createFile()}.
     *
     * @param path Path as array of Strings or Files
     * @return file handle
     */
    public TestFile file(Object... path) {
        try {
            return new TestFile(this, path);
        } catch (RuntimeException e) {
            throw new RuntimeException(String.format("Could not locate file '%s' relative to '%s'.", Arrays.toString(path), this), e);
        }
    }

    /**
     * Writes plain-text content to this file using UTF-8 encoding.
     *
     * @param content Plain-text content
     * @return this
     */
    public TestFile setText(String content) {
        try {
            FileUtils.writeStringToFile(this, content.toString(), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not write to test file '%s'", this), e);
        }

        assertIsFile();
        return this;
    }

    /**
     * Reads plain-text content from this file using UTF-8 encoding.
     *
     * @return Plain-text content
     */
    public String getText() {
        assertIsFile();

        try {
            return FileUtils.readFileToString(this, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not read from test file '%s'", this), e);
        }
    }

    /**
     * Creates a new file if it doesn't exist yet.
     *
     * @return this
     */
    public TestFile createFile() {
        TestFile parentDir = new TestFile(getParentFile());

        if (!parentDir.exists()) {
            parentDir.createDirectory();
        }

        if (!isFile()) {
            try {
                this.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        assertIsFile();
        return this;
    }

    /**
     * Creates a new directory if it doesn't exist yet.
     *
     * @return this
     */
    public TestFile createDirectory() {
        if (mkdirs()) {
            return this;
        }

        if (isDirectory()) {
            return this;
        }

        throw new AssertionError("Problems creating dir: " + this
            + ". Diagnostics: exists=" + this.exists() + ", isFile=" + this.isFile() + ", isDirectory=" + this.isDirectory());
    }

    /**
     * Lists directories and files for this file.
     *
     * @return Lists directories and files
     */
    @Override
    public TestFile[] listFiles() {
        File[] children = super.listFiles();

        if (children == null) {
            return null;
        }

        TestFile[] files = new TestFile[children.length];

        for (int i = 0; i < children.length; i++) {
            File child = children[i];
            files[i] = new TestFile(child);
        }

        return files;
    }

    private TestFile assertIsFile() {
        if (!isFile()) {
            throw new AssertionError(String.format("%s is not a file", this));
        }

        return this;
    }

    private static File join(File file, Object[] path) {
        File current = file.getAbsoluteFile();

        for (Object p : path) {
            current = new File(current, p.toString());
        }

        try {
            return current.getCanonicalFile();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Could not canonicalize '%s'.", current), e);
        }
    }
}
