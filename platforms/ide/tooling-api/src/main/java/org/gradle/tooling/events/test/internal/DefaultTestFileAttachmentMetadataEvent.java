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

package org.gradle.tooling.events.test.internal;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.TestFileAttachmentMetadataEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Consumer implementation of file attachment event
 */
@NullMarked
public class DefaultTestFileAttachmentMetadataEvent extends AbstractTestMetadataEvent implements TestFileAttachmentMetadataEvent {
    private final File file;
    @Nullable
    private final String mediaType;

    public DefaultTestFileAttachmentMetadataEvent(long eventTime, OperationDescriptor descriptor, File file, @Nullable String mediaType) {
        super(eventTime, descriptor);
        this.file = file;
        this.mediaType = mediaType;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public @Nullable String getMediaType() {
        return mediaType;
    }
}
