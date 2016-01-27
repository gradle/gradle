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
package org.gradle.api.file;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Information about a file in a {@link FileTree}.
 */
public interface FileTreeElement {
    /**
     * Returns the file being visited.
     *
     * @return The file. Never returns null.
     */
    File getFile();

    /**
     * Returns true if this element is a directory, or false if this element is a regular file.
     *
     * @return true if this element is a directory.
     */
    boolean isDirectory();

    /**
     * Returns the last modified time of this file at the time of file traversal.
     *
     * @return The last modified time.
     */
    long getLastModified();

    /**
     * Returns the size of this file at the time of file traversal.
     *
     * @return The size, in bytes.
     */
    long getSize();

    /**
     * Opens this file as an input stream. Generally, calling this method is more performant than calling {@code new
     * FileInputStream(getFile())}.
     *
     * @return The input stream. Never returns null. The caller is responsible for closing this stream.
     */
    InputStream open();

    /**
     * Copies the content of this file to an output stream. Generally, calling this method is more performant than
     * calling {@code new FileInputStream(getFile())}.
     *
     * @param output The output stream to write to. The caller is responsible for closing this stream.
     */
    void copyTo(OutputStream output);

    /**
     * Copies this file to the given target file. Does not copy the file if the target is already a copy of this file.
     *
     * @param target the target file.
     * @return true if this file was copied, false if it was up-to-date
     */
    boolean copyTo(File target);

    /**
     * Returns the base name of this file.
     *
     * @return The name. Never returns null.
     */
    String getName();

    /**
     * Returns the path of this file, relative to the root of the containing file tree. Always uses '/' as the hierarchy
     * separator, regardless of platform file separator. Same as calling <code>getRelativePath().getPathString()</code>.
     *
     * @return The path. Never returns null.
     */
    String getPath();

    /**
     * Returns the path of this file, relative to the root of the containing file tree.
     *
     * @return The path. Never returns null.
     */
    RelativePath getRelativePath();

    /**
     * Returns the Unix permissions of this file, e.g. {@code 0644}.
     *
     * @return The Unix file permissions.
     */
    int getMode();
}
