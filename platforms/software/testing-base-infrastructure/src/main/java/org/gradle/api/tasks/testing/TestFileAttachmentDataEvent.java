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

package org.gradle.api.tasks.testing;

import org.gradle.api.Incubating;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * File attachment data published by a test.
 *
 * @since 9.4.0
 */
@Incubating
@NullMarked
public interface TestFileAttachmentDataEvent extends TestMetadataEvent {
    /**
     * Returns the path to the attached file.
     *
     * @return path to the attached file
     * @since 9.4.0
     */
    Path getPath();

    /**
     * Returns the media type of the attached file, or null if not specified.
     *
     * @return media type of the attached file, or null if not specified
     * @since 9.4.0
     */
    @Nullable
    String getMediaType();
}
