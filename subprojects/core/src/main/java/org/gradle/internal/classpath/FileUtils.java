/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

public class FileUtils {
    /**
     * Checks if the channel created with the given options will have read access of the file content.
     * See the contract of {@link Files#newByteChannel(Path, Set, FileAttribute[])}.
     */
    public static boolean optionsAllowReading(OpenOption[] options) {
        boolean hasNonReadMode = false;

        for (OpenOption option : options) {
            if (option == StandardOpenOption.READ) {
                return true;
            }
            if (option == StandardOpenOption.APPEND || option == StandardOpenOption.WRITE) {
                hasNonReadMode = true;
            }
        }

        return !hasNonReadMode;
    }

    public static boolean optionsAllowReading(Set<?> options) {
        return optionsAllowReading(Cast.<Set<OpenOption>>uncheckedNonnullCast(options).toArray(new OpenOption[0]));
    }

    public static void tryReportFileOpened(Path path, String consumer) {
        File file = toFileIfAvailable(path);
        if (file != null) {
            Instrumented.fileOpened(file, consumer);
        }
    }

    public static void tryReportDirectoryContentObserved(Path path, String consumer) {
        File file = toFileIfAvailable(path);
        if (file != null) {
            Instrumented.directoryContentObserved(file, consumer);
        }
    }

    public static void tryReportFileSystemEntryObserved(Path path, String consumer) {
        File file = toFileIfAvailable(path);
        if (file != null) {
            Instrumented.fileSystemEntryObserved(file, consumer);
        }
    }

    /**
     * Returns the result of {@link Path#toFile()} if that will not throw an exception, null otherwise.
     */
    @Nullable
    private static File toFileIfAvailable(Path path) {
        if (path.getFileSystem() == FileSystems.getDefault()) {
            return path.toFile();
        }
        return null;
    }
}
