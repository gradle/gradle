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
package org.gradle.api.file;

import org.gradle.api.Transformer;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;

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
     * Returns the last modified time of this file. Generally, calling this method is more performant than calling
     * {@code getFile().lastModified()}
     *
     * @return The last modified time.
     */
    long getLastModified();

    /**
     * Opens this file as an input stream. Generally, calling this method is more performant than calling {@code new
     * FileInputStream(getFile())}.
     *
     * @return The input stream. Never returns null. The caller is responsible for closing this stream.
     */
    InputStream open();

    /**
     * Copies this file to the given target file. Does not copy the file if the target is already a copy of this file.
     *
     * @param target the target file.
     * @return true if this file was copied, false if it was up-to-date
     */
    boolean copyTo(File target);

    /**
     * Copies this file to the given target file, transforming the content as it is copied. Does not copy the file it
     * the target is already a copy of this file.
     *
     * @param target the target file.
     * @param readerFactory creates the transforming reader from the content reader.
     * @return true if this file was copied, false it is was up-to-date
     */
    boolean copyTo(File target, Transformer<Reader> readerFactory);

    /**
     * Returns the path of the file being visited, relative to the root of the containing file tree.
     *
     * @return The path. Never returns null.
     */
    RelativePath getRelativePath();
}
