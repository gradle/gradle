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

package org.gradle.api.internal.tasks.testing;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;

/**
 * File attachment data published by a test.
 *
 * This implementation is intended to be kept within the build process and workers.
 */
@NullMarked
public class DefaultTestFileAttachmentDataEvent extends AbstractTestDataEvent {
    private final Path path;
    private final @Nullable String mediaType;

    public DefaultTestFileAttachmentDataEvent(Instant logTime, Path path, @Nullable String mediaType) {
        super(logTime);

        this.path = path;
        this.mediaType = mediaType;
    }

    public Path getPath() {
        return path;
    }

    @Nullable
    public String getMediaType() {
        return mediaType;
    }
}
