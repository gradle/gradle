/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Nullable;

import java.io.File;

/**
 * A {@code Resource} represents some binary artifact.
 *
 * <p>Implementations are not required to be thread-safe.</p>
 *
 * <p>This type will be merged with {@link ExternalResource} and friends.</p>
 */
public interface Resource {
    /**
     * Returns a display name for this resource. This can be used in log and error messages.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Returns the location of this resource.
     */
    ResourceLocation getLocation();

    /**
     * Returns a file that contains the same content as this resource, encoded using some default charset. Not all resources are available as a file.
     * Note that this method may return null when {@link ResourceLocation#getFile()} returns non-null, when the contents are different.
     *
     * @return A file containing this resource. Returns null if this resource is not available as a file.
     */
    @Nullable
    File getFile();

    /**
     * Returns true when the content of this resource is cached in-heap or uses a hard-coded value. Returns false when the content requires IO on each query.
     *
     * <p>When this method returns false, the caller should avoid querying the content more than once.</p>
     */
    boolean isContentCached();

    /**
     * Returns true if this resource exists, false if it does not exist. A resource exists when it has content associated with it.
     *
     * <p>Note that this method may be expensive when {@link #isContentCached()} returns false, depending on the implementation.
     *
     * @return true if this resource exists.
     * @throws ResourceException On failure to check whether resource exists.
     */
    boolean getExists() throws ResourceException;

    /**
     * Returns true when the content of this resource is empty. This method is may be more efficient than calling {@link #getText()} and checking the length.
     *
     * <p>Note that this method may be expensive when {@link #isContentCached()} returns false, depending on the implementation.
     *
     * @throws ResourceNotFoundException When this resource does not exist.
     * @throws ResourceException On failure to read content.
     */
    boolean getHasEmptyContent() throws ResourceNotFoundException, ResourceException;

    /**
     * Returns the content of this resource, as a String.
     *
     * <p>Note that this method may be expensive when {@link #isContentCached()} returns false, depending on the implementation.
     *
     * @return the content. Never returns null.
     * @throws ResourceNotFoundException When this resource does not exist.
     * @throws ResourceException On failure to read content.
     */
    String getText() throws ResourceNotFoundException, ResourceException;
}
