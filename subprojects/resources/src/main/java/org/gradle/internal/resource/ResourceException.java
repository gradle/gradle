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

import org.gradle.api.GradleException;
import org.gradle.internal.exceptions.Contextual;

import java.net.URI;

@Contextual
public class ResourceException extends GradleException {
    private final URI location;

    public ResourceException(URI location, String message) {
        super(message);
        this.location = location;
    }

    public ResourceException(URI location, String message, Throwable cause) {
        super(message, cause);
        this.location = location;
    }

    public ResourceException(String message, Throwable cause) {
        super(message, cause);
        this.location = null;
    }

    public static ResourceException getFailed(URI location, Throwable failure) {
        return failure(location, String.format("Could not get resource '%s'.", location), failure);
    }

    public static ResourceException putFailed(URI location, Throwable failure) {
        return failure(location, String.format("Could not write to resource '%s'.", location), failure);
    }

    /**
     * Wraps the given failure, unless it is a ResourceException with the specified location.
     */
    public static ResourceException failure(URI location, String message, Throwable failure) {
        if (failure instanceof ResourceException) {
            ResourceException resourceException = (ResourceException) failure;
            if (location.equals(resourceException.getLocation())) {
                return resourceException;
            }
        }
        return new ResourceException(location, message, failure);
    }

    public URI getLocation() {
        return location;
    }
}
