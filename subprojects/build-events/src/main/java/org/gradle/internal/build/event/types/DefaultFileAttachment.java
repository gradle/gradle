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

package org.gradle.internal.build.event.types;

import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;

@SuppressWarnings("unused")
public class DefaultFileAttachment implements Serializable {
    private final File file;
    private final @Nullable String mediaType;

    public DefaultFileAttachment(Path path, @Nullable String mediaType) {
        this.file = path.toFile();
        this.mediaType = mediaType;
    }

    public File getFile() {
        return file;
    }

    @Nullable
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String toString() {
        return "FileAttachment{" +
            "file=" + file +
            ", mediaType='" + mediaType + '\'' +
            '}';
    }
}
