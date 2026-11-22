/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file.temp;

import org.gradle.internal.file.nio.OwnerOnlyFiles;
import org.jspecify.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;

/**
 * Security safe API's for creating temporary files.
 */
public final class TempFiles {

    private TempFiles() {
        /* no-op */
    }

    /**
     * Improves the security guarantees of {@link File#createTempFile(String, String, File)}.
     *
     * @see File#createTempFile(String, String, File)
     */
    @CheckReturnValue
    static File createTempFile(@Nullable String prefix, @Nullable String suffix, File directory) throws IOException {
        return File.createTempFile(normalizePrefix(prefix), suffix, requireDirectory(directory));
    }

    /**
     * Like {@link #createTempFile(String, String, File)}, but additionally restricts the created
     * file to owner-only read/write permissions ({@code rw-------}) on POSIX filesystems, applied
     * atomically at creation time so there is no world-readable window. Use this for temporary
     * files that may hold sensitive data.
     *
     * <p>On non-POSIX filesystems the permission attribute is skipped and the platform default
     * ACLs apply.
     */
    @CheckReturnValue
    static File createOwnerOnlyTempFile(@Nullable String prefix, @Nullable String suffix, File directory) throws IOException {
        return OwnerOnlyFiles.createTempFile(requireDirectory(directory), normalizePrefix(prefix), suffix);
    }

    private static File requireDirectory(@Nullable File directory) {
        if (directory == null) {
            throw new NullPointerException("The `directory` argument must not be null as this will default to the system temporary directory");
        }
        return directory;
    }

    private static String normalizePrefix(@Nullable String prefix) {
        if (prefix == null) {
            prefix = "gradle-";
        }
        if (prefix.length() <= 3) {
            prefix = "tmp-" + prefix;
        }
        return prefix;
    }
}
