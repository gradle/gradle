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

import org.gradle.api.Incubating;
import org.gradle.api.NonExtensible;
import org.gradle.internal.HasInternalProtocol;

/**
 * <p>Provides details about a file or directory about to be copied, and allows some aspects of the destination file to
 * be modified.</p>
 *
 * <p>Using this interface, you can change the destination path of the file, filter the content of the file, or exclude
 * the file from the result entirely.</p>
 */
@HasInternalProtocol
@NonExtensible
public interface FileCopyDetails extends FileTreeElement, ContentFilterable {
    /**
     * Excludes this file from the copy.
     */
    void exclude();

    /**
     * Sets the destination name of this file.
     *
     * @param name The destination name of this file.
     */
    void setName(String name);

    /**
     * Sets the destination path of this file.
     *
     * @param path The path of this file.
     */
    void setPath(String path);

    /**
     * Sets the destination path of this file.
     *
     * @param path the new path for this file.
     */
    void setRelativePath(RelativePath path);

    /**
     * Sets the Unix permissions of this file.
     *
     * @param mode the Unix permissions, e.g. {@code 0644}.
     */
    void setMode(int mode);

    /**
     * The strategy to use if there is already a file at this file's destination.
     */
    @Incubating
    void setDuplicatesStrategy(DuplicatesStrategy strategy);

    /**
     * The strategy to use if there is already a file at this file's destination.
     * <p>
     * The value can be set with a case insensitive string of the enum value (e.g. {@code 'exclude'} for {@link DuplicatesStrategy#EXCLUDE}).
     *
     * @see DuplicatesStrategy
     * @return the strategy to use for this file.
     */
    @Incubating
    DuplicatesStrategy getDuplicatesStrategy();

    /**
     * Returns the base name of this file at the copy destination.
     *
     * @return The destination name. Never returns null.
     */
    String getName();

    /**
     * Returns the path of this file, relative to the root of the copy destination.
     * <p>
     * Always uses '/' as the hierarchy separator, regardless of platform file separator.
     * Same as calling <code>getRelativePath().getPathString()</code>.
     *
     * @return The path, relative to the root of the copy destination. Never returns null.
     */
    String getPath();

    /**
     * Returns the path of this file, relative to the root of the copy destination.
     *
     * @return The path, relative to the root of the copy destination. Never returns null.
     */
    RelativePath getRelativePath();

    /**
     * Returns the base name of this file at the copy source.
     *
     * @return The source name. Never returns null.
     */
    String getSourceName();

    /**
     * Returns the path of this file, relative to the root of the containing file tree.
     * <p>
     * Always uses '/' as the hierarchy separator, regardless of platform file separator.
     * Same as calling <code>getRelativeSourcePath().getPathString()</code>.
     *
     * @return The path, relative to the root of the containing file tree. Never returns null.
     */
    String getSourcePath();

    /**
     * Returns the path of this file, relative to the root of the containing file tree.
     *
     * @return The path, relative to the root of the containing file tree. Never returns null.
     */
    RelativePath getRelativeSourcePath();

}
