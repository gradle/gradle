/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.resources;

import org.gradle.api.file.RelativePath;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.resource.Resource;
import org.gradle.internal.resource.ResourceContentMetadataSnapshot;

import java.io.IOException;
import java.io.InputStream;

public interface SnapshottableResource extends Resource {
    /**
     * The absolute path of this resource. Can safely be used as a cache key.
     */
    String getPath();

    /**
     * Returns the base name of this resource.
     */
    String getName();

    /**
     * Path of this resource relative to the root from which it was included.
     */
    RelativePath getRelativePath();

    /**
     * The type of resource.
     */
    FileType getType();

    /**
     * Is this resource a root element?
     * TODO wolfs: remove this
     */
    boolean isRoot();

    /**
     * Returns an unbuffered {@link InputStream} that provides means to read the resource. It is the caller's responsibility to close this stream.
     * Some resources may only allow that the stream is read once.
     *
     * @return An input stream.
     */
    InputStream read() throws IOException;

    /**
     * Returns a snapshot of the contents of this resource.
     */
    ResourceContentMetadataSnapshot getContent();
}
