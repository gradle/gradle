/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.nativeintegration.filesystem;

import org.gradle.internal.file.FileMetadata;

import java.io.File;

public interface FileMetadataAccessor {
    /**
     * Gets the file metadata of a {@link File}.
     * <p>
     * If the type of the file cannot be determined, or is
     * neither {@link org.gradle.internal.file.FileType#RegularFile}
     * nor {@link org.gradle.internal.file.FileType#Directory},
     * then the file type of the file metadata is of type
     * {@link org.gradle.internal.file.FileType#Missing}.
     * <p>
     * Such cases include:
     * <ul>
     *     <li>actual missing files</li>
     *     <li>broken symlinks</li>
     *     <li>circular symlinks</li>
     *     <li>named pipes</li>
     * </ul>
     */
    FileMetadata stat(File f);
}
