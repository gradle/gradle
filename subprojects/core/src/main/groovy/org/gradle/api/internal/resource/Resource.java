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

package org.gradle.api.internal.resource;

import java.io.File;
import java.io.Serializable;
import java.net.URI;

/**
 * A {@code Resource} represents some binary artifact.
 *
 * <p>Implementations are not required to be thread-safe.</p>
 */
public interface Resource extends Serializable {
    /**
     * Returns a display name for this resource. This can be used in log and error messages.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Returns a file representing this resource. Not all resources are available as a file.
     *
     * @return A file representing this resource. Returns null if this resource is not available as a file.
     */
    File getFile();

    /**
     * Returns the URI for this resource. Not all resources have a URI.
     *
     * @return The URI for this resource. Returns null if this resource does not have a URI.
     */
    URI getURI();

    /**
     * Returns true if this resource exists, false if it does not exist.
     *
     * @return true if this resource exists.
     */
    boolean getExists();

    /**
     * Returns the content of this resource, as a String.
     *
     * @return the content. Never returns null.
     * @throws ResourceNotFoundException When this resource does not exist.
     */
    String getText() throws ResourceNotFoundException;
}
