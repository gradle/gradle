/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.events.test;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * A file attachment was published by an executed test.
 *
 * @since 9.3.0
 */
@Incubating
@NullMarked
public interface FileAttachment {
    /**
     * Path to the published file attachment.
     *
     * The path may represent a single file or a directory.
     *
     * @return path to the file
     * @since 9.3.0
     */
    File getPath();

    /**
     * <a href="https://www.iana.org/assignments/media-types/media-types.xhtml">Media type</a> of the file.
     *
     * @return media type of the file attachment or {@code null} if the file attachment represents a directory
     * @since 9.3.0
     */
    @Nullable
    String getMediaType();
}
