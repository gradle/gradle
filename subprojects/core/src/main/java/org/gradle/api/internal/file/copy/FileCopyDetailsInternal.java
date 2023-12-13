/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.file.copy;

import org.gradle.api.file.FileCopyDetails;

/**
 * @implSpec Implementations representing a file copy of an entry in a compressed archive should return {@code true} from {@link #isCompressedArchiveEntry()}.
 */
public interface FileCopyDetailsInternal extends FileCopyDetails {

    boolean isDefaultDuplicatesStrategy();

    CopySpecResolver getSpecResolver();

    /**
     * Obtain a display name that represents what is being copied.
     *
     * @return display name
     */
    String getDisplayName();

    /**
     * Checks if this file copy details represents a file copy of an entry in a compressed archive.
     *
     * @return {@code true} if this file copy details represents a file copy of an entry in a compressed archive; {@code false} otherwise
     */
    default boolean isCompressedArchiveEntry() {
        return false;
    }
}
