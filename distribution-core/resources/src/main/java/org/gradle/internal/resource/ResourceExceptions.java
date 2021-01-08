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

import org.gradle.api.resources.MissingResourceException;
import org.gradle.api.resources.ResourceException;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;

public class ResourceExceptions {
    public static ResourceIsAFolderException readFolder(File location) {
        return new ResourceIsAFolderException(location.toURI(), String.format("Cannot read '%s' because it is a folder.", location));
    }

    public static ResourceException readFailed(File location, Throwable failure) {
        return failure(location.toURI(), String.format("Could not read '%s'.", location), failure);
    }

    public static ResourceException readFailed(String displayName, Throwable failure) {
        return new ResourceException(String.format("Could not read %s.", displayName), failure);
    }

    public static MissingResourceException readMissing(File location, Throwable failure) {
        return new MissingResourceException(location.toURI(),
                String.format("Could not read '%s' as it does not exist.", location),
                failure instanceof FileNotFoundException ? null : failure);
    }

    public static MissingResourceException getMissing(URI location, Throwable failure) {
        return new MissingResourceException(location,
                String.format("Could not read '%s' as it does not exist.", location),
                failure instanceof FileNotFoundException ? null : failure);
    }

    public static MissingResourceException getMissing(URI location) {
        return new MissingResourceException(location,
                String.format("Could not read '%s' as it does not exist.", location));
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
}
