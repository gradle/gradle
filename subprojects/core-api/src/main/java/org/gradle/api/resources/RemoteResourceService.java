/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

/**
 * A service which allows fetching external resources by URI.
 * This service honors the offline mode and resources downloaded
 * via this service are cached.
 *
 * @since 7.3
 */
@Incubating
public interface RemoteResourceService {
    /**
     * Downloads an external resource and executes the consumer against the
     * resource input stream.
     * @param uri the URI of the external resource
     * @param consumer the consumer
     */
    void withResource(URI uri, String displayName, Action<? super RemoteResource> consumer);

    /**
     * Represents a remote resource, for processing.
     *
     * @since 7.3
     */
    @Incubating
    interface RemoteResource {
        /**
         * Determines if Gradle runs in offline mode
         * @return true if offline
         */
        boolean isOffline();

        /**
         * Persists the remote resource into the destination file. This action
         * is not executed when offline.
         * @param destination the destination file
         */
        void persistInto(File destination);

        /**
         * Executes an action with an input stream to the resource. This action
         * is not executed when offline
         * @param action the action to execute on the resource
         */
        void withContent(Action<? super InputStream> action);
    }
}
