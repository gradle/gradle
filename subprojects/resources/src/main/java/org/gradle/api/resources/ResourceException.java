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

package org.gradle.api.resources;

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;

import javax.annotation.Nullable;
import java.net.URI;

/**
 * Generic resource exception that all other resource-related exceptions inherit from.
 */
@Contextual
public class ResourceException extends GradleException {
    private final URI location;

    public ResourceException() {
        location = null;
    }

    public ResourceException(String message) {
        super(message);
        location = null;
    }

    public ResourceException(String message, Throwable cause) {
        super(message, cause);
        location = null;
    }

    public ResourceException(URI location, String message) {
        super(message);
        this.location = location;
    }

    public ResourceException(URI location, String message, Throwable cause) {
        super(message, cause);
        this.location = location;
    }

    /**
     * Returns the location of the resource, if known.
     *
     * @return The location, or null if not known.
     */
    @Nullable
    public URI getLocation() {
        return location;
    }
}
