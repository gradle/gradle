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
 * Information about a file in a directory/file tree.
 */
public interface FileTreeElement extends ReadOnlyFileTreeElement {
    /**
     * Returns the file being visited.
     *
     * @return The file. Never returns null.
     * @since 0.9
     */
    File getFile();

    /**
     * Opens this file as an input stream. Generally, calling this method is more performant than calling {@code new
     * FileInputStream(getFile())}.
     *
     * @return The input stream. Never returns null. The caller is responsible for closing this stream.
     * @since 0.9
     */
    InputStream open();

    /**
     * Copies the content of this file to an output stream. Generally, calling this method is more performant than
     * calling {@code new FileInputStream(getFile())}.
     *
     * @param output The output stream to write to. The caller is responsible for closing this stream.
     * @since 0.9
     */
    void copyTo(OutputStream output);

    /**
     * Copies this file to the given target file. Does not copy the file if the target is already a copy of this file.
     *
     * @param target the target file.
     * @return true if this file was copied, false if it was up-to-date
     * @since 0.9
     */
    boolean copyTo(File target);
}
