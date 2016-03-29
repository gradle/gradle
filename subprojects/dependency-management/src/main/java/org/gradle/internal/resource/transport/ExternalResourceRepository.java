/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transport;

import org.gradle.api.Nullable;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.local.LocalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.net.URI;
import java.util.List;

public interface ExternalResourceRepository {
    /**
     * Returns a copy of this repository with progress logging enabled.
     */
    ExternalResourceRepository withProgressLogging();

    /**
     * Attempts to fetch the given resource.
     *
     * @param source The location of the resource to obtain
     * @param revalidate Ensure the external resource is not stale
     * @return null if the resource is not found.
     * @throws ResourceException On failure to fetch resource.
     */
    @Nullable
    ExternalResource getResource(URI source, boolean revalidate) throws ResourceException;

    /**
     * Transfer a resource to the repository
     *
     * @param source The local resource to be transferred.
     * @param destination Where to transfer the resource.
     * @throws IOException On publication failure.
     */
    void put(LocalResource source, URI destination) throws IOException;

    /**
     * Fetches only the metadata for the result.
     *
     * @param source The location of the resource to obtain the metadata for
     * @param revalidate Ensure the external resource is not stale
     * @return The resource metadata, or null if the resource does not exist
     * @throws ResourceException On failure to fetch resource metadata.
     */
    @Nullable
    ExternalResourceMetaData getResourceMetaData(URI source, boolean revalidate) throws ResourceException;

    /**
     * Return a listing of child resources names.
     *
     * @param parent The parent directory from which to generate the listing.
     * @return A listing of the direct children of the given parent. Returns null when the parent resource does not exist.
     * @throws ResourceException On listing failure.
     */
    @Nullable
    List<String> list(URI parent) throws ResourceException;
}
