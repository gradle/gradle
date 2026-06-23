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

import org.jspecify.annotations.Nullable;

import javax.annotation.CheckReturnValue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

/**
 * Security safe API's for creating temporary files.
 */
public final class TempFiles {

    private static final FileAttribute<Set<PosixFilePermission>> OWNER_ONLY_ATTRIBUTE =
        PosixFilePermissions.asFileAttribute(
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        );

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
        if (directory == null) {
            throw new NullPointerException("The `directory` argument must not be null as this will default to the system temporary directory");
        }
        if (prefix == null) {
            prefix = "gradle-";
        }
        if (prefix.length() <= 3) {
            prefix = "tmp-" + prefix;
        }
        Path dir = directory.toPath();
        if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
            return Files.createTempFile(dir, prefix, suffix, OWNER_ONLY_ATTRIBUTE).toFile();
        }
        return Files.createTempFile(dir, prefix, suffix).toFile();
    }
}
