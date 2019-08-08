/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.file.impl;

import java.io.File;
import java.io.IOException;

/**
 * A file deleter that doesn't give up if deletion doesn't work on the first try.
 */
public interface Deleter {
    /**
     * Attempts to delete the given file or directory recursively.
     *
     * Can delete directories with contents.
     * Follows symlinks pointing to directories when instructed to.
     *
     * @return {@code true} if anything was removed, {@code false} if no change was
     *         attempted (because {@code target} didn't exist).
     *
     * @throws IOException when {@code target} cannot be deleted (with detailed error
     *         message).
     */
    boolean deleteRecursively(File target, boolean followSymlinks) throws IOException;

    /**
     * Attempts to clean the given directory recursively, removing all of its contents.
     *
     * Does nothing when {@code target} is a regular file.
     * Follows symlinks pointing to directories when instructed to.
     *
     * @return {@code true} if anything was removed, {@code false} if no change was
     *         attempted (because {@code target} didn't exist).
     *
     * @throws IOException when {@code target} cannot be deleted (with detailed error
     *         message).
     */
    boolean cleanRecursively(File target, boolean followSymlinks) throws IOException;

    /**
     * Attempts to delete a single file or an empty directory.
     *
     * Does not follow symlinks.
     *
     * @return {@code true} if the removal was successful, {@code false} otherwise
     *         (because {@code target} didn't exist).
     */
    boolean delete(File target);
}
