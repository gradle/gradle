/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.history.changes;

import org.gradle.internal.file.FileType;

/**
 * The absolute path and the type of a file.
 *
 * Used to construct {@link DefaultFileChange}s.
 */
public class FilePathWithType {
    private final String absolutePath;
    private final FileType fileType;

    public FilePathWithType(String absolutePath, FileType fileType) {
        this.absolutePath = absolutePath;
        this.fileType = fileType;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public FileType getFileType() {
        return fileType;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", fileType, absolutePath);
    }
}
