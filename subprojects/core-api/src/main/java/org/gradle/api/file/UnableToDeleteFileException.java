/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.UncheckedIOException;

import java.io.File;

/**
 * Thrown by Gradle when it is unable to delete a file.
 *
 * @deprecated This exception is not thrown anymore, and is replaced by a {@link RuntimeException}.
 */
@Deprecated
public class UnableToDeleteFileException extends UncheckedIOException {

    private final File file;

    /**
     * Creates exception with file, a reasonable message is used.
     */
    public UnableToDeleteFileException(File file) {
        super(toMessage(file));
        this.file = file;
    }

    /**
     * Creates exception with file and message.
     *
     * @since 5.3
     */
    @Incubating
    public UnableToDeleteFileException(File file, String message) {
        super(message);
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    private static String toMessage(File file) {
        return String.format("Unable to delete %s: %s", file.isDirectory() ? "directory" : "file", file.getAbsolutePath());
    }
}
