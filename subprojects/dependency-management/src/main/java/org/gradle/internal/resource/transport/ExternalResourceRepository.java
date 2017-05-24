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
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.LocalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.util.List;

public interface ExternalResourceRepository {
    /**
     * Returns a copy of this repository with progress logging enabled.
     */
    ExternalResourceRepository withProgressLogging();

    /**
     * Returns the resource with the given name. Note that this method does not access the resource in any way. To do that, use the methods on the returned resource.
     *
     * @param resource The location of the resource
     * @param revalidate Ensure the external resource is not stale when reading its content
     */
    ExternalResource resource(ExternalResourceName resource, boolean revalidate);

    /**
     * Returns the resource with the given name. Note that this method does not access the resource in any way. To do that, use the methods on the returned resource.
     *
     * @param resource The location of the resource
     */
    ExternalResource resource(ExternalResourceName resource);

    /**
     * Transfer a resource to the repository
     *
     * @param source The local resource to be transferred.
     * @param destination Where to transfer the resource.
     * @throws IOException On publication failure.
     */
    void put(LocalResource source, ExternalResourceName destination) throws IOException;

    /**
     * Fetches only the metadata for the result.
     *
     * @param source The location of the resource to obtain the metadata for
     * @param revalidate Ensure the external resource is not stale
     * @return The resource metadata, or null if the resource does not exist
     * @throws ResourceException On failure to fetch resource metadata.
     */
    @Nullable
    ExternalResourceMetaData getResourceMetaData(ExternalResourceName source, boolean revalidate) throws ResourceException;

    /**
     * Return a listing of child resources names.
     *
     * @param parent The parent directory from which to generate the listing.
     * @return A listing of the direct children of the given parent. Returns null when the parent resource does not exist.
     * @throws ResourceException On listing failure.
     */
    @Nullable
    List<String> list(ExternalResourceName parent) throws ResourceException;
}
