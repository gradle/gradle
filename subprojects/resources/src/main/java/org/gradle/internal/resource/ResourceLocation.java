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

package org.gradle.internal.resource;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;

/**
 * Represents the location or identity of a resource.
 */
public interface ResourceLocation {
    /**
     * Returns a display name for the resource. This can be used in log and error messages.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Returns a file representing the location of the resource. Not all resources are available as a file.
     * Note that the file returned by this method may not necessarily have the same contents as the resource. For example, the file may be compressed,
     * contain text encoded with a different encoding or represent a directory.
     *
     * @return A file location this resource. Returns null if this resource is not available as a file.
     */
    @Nullable
    File getFile();

    /**
     * Returns the URI for this resource. Not all resources have a URI.
     * Note that the URI returned by this method may not necessarily have the same contents as the resource. For example, the file may be compressed,
     * contain text encoded with a different encoding or represent a directory.
     *
     * @return The URI for this resource. Returns null if this resource does not have a URI.
     */
    @Nullable
    URI getURI();
}
