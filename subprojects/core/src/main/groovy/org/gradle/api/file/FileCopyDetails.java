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

/**
 * <p>Provides details about a file or directory about to be copied, and allows some aspects of the destination file to
 * be modified.</p>
 *
 * <p>Using this interface, you can change the destination path of the file, filter the content of the file, or exclude
 * the file from the result entirely.</p>
 */
public interface FileCopyDetails extends FileTreeElement, ContentFilterable {
    /**
     * Excludes this file from the copy.
     */
    void exclude();

    /**
     * Sets the destination name of this file.
     *
     * @param name The name of this file.
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
     * Sets the strategy for handling duplicates.
     *
     * @param strategy the strategy used when a duplicate file is encountered.
     * Either 'include', 'exclude', or 'inherit'
     */
    @Incubating
    void setDuplicatesStrategy(DuplicatesStrategy strategy);

    /**
     * Get the strategy for handling duplicates, either
     * DuplicatesStrategy.include if duplicate files of this type
     * should be retained or DuplicatesStrategy.exclude otherwise.
     *
     * @return the duplicates strategy for this file
     */
    @Incubating
    DuplicatesStrategy getDuplicatesStrategy();

}
