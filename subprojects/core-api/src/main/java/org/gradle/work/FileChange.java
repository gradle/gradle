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

package org.gradle.work;

import org.gradle.api.file.FileType;

import java.io.File;

/**
 * A change to a file.
 *
 * @since 5.4
 */
public interface FileChange {

    /**
     * The file, which may no longer exist.
     */
    File getFile();

    /**
     * The type of change to the file.
     */
    ChangeType getChangeType();

    /**
     * The file type of the file.
     *
     * <p>
     *     For {@link ChangeType#ADDED} and {@link ChangeType#MODIFIED}, the type of the file which was added/modified is reported.
     *     For {@link ChangeType#REMOVED} the type of the file which was removed is reported.
     * </p>
     */
    FileType getFileType();

    /**
     * The normalized path of the file, as specified by the path normalization strategy.
     *
     * <p>
     *    See {@link org.gradle.api.tasks.PathSensitivity}, {@link org.gradle.api.tasks.Classpath} and {@link org.gradle.api.tasks.CompileClasspath} for the different path normalization strategies.
     * </p>
     */
    String getNormalizedPath();
}
