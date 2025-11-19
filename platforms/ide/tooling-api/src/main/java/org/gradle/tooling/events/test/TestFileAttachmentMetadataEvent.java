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
 * An event emitted by tests that contains file attachment data.
 *
 * @since 9.4.0
 */
@NullMarked
@Incubating
public interface TestFileAttachmentMetadataEvent extends TestMetadataEvent {
    /**
     * The published file attachment.
     *
     * The path may represent a single file or a directory.
     *
     * @return the file
     * @since 9.4.0
     */
    File getFile();

    /**
     * <a href="https://www.iana.org/assignments/media-types/media-types.xhtml">Media type</a> of the file, if known.
     * This is used to declare the general type of data provided by the file.
     * <p>
     * A media type may be provided by the publishing test or omitted. Some files, like directories, do not have a conventional media type.
     * </p>
     *
     * @return media type, may be {@code null}
     * @since 9.4.0
     */
    @Nullable
    String getMediaType();
}
