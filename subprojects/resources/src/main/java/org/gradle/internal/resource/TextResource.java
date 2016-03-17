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
import org.gradle.api.resources.ResourceException;

import java.io.File;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * A {@code Resource} that has text content.
 */
public interface TextResource extends Resource {
    /**
     * Returns the location of this resource.
     */
    ResourceLocation getLocation();

    /**
     * Returns a file that contains the same content as this resource, encoded using the charset specified by {@link #getCharset()}.
     * Not all resources are available as a file.
     * Note that this method may return null when {@link ResourceLocation#getFile()} returns non-null, when the contents are different.
     *
     * @return A file containing this resource. Returns null if this resource is not available as a file.
     */
    @Nullable
    File getFile();

    /**
     * Returns the charset use to encode the file containing the resource's content, as returned by {@link #getFile()}.
     *
     * @return The charset. Returns null when this resource is not available as a file.
     */
    @Nullable
    Charset getCharset();

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
     * @throws org.gradle.api.resources.MissingResourceException When this resource does not exist.
     * @throws ResourceException On failure to read content.
     */
    boolean getHasEmptyContent() throws ResourceException;

    /**
     * Returns an *unbuffered* reader over the content of this resource.
     *
     * <p>Note that this method, or reading from the provided reader, may be expensive when {@link #isContentCached()} returns false, depending on the implementation.
     *
     * @throws org.gradle.api.resources.MissingResourceException When this resource does not exist.
     * @throws ResourceException On failure to read content.
     */
    Reader getAsReader() throws ResourceException;

    /**
     * Returns the content of this resource, as a String.
     *
     * <p>Note that this method may be expensive when {@link #isContentCached()} returns false, depending on the implementation.
     *
     * @return the content. Never returns null.
     * @throws org.gradle.api.resources.MissingResourceException When this resource does not exist.
     * @throws ResourceException On failure to read content.
     */
    String getText() throws ResourceException;
}
