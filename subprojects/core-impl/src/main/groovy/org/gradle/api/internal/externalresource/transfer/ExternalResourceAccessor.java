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

package org.gradle.api.internal.externalresource.transfer;

import org.gradle.api.Nullable;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.internal.hash.HashValue;

import java.io.IOException;

public interface ExternalResourceAccessor {

    /**
     * Obtain the resource at the given location.
     *
     * If the resource does not exist, this method should return null.
     *
     * If the resource may exist but can't be accessed due to some configuration issue, the implementation
     * may either return null or throw an {@link IOException} to indicate a fatal condition.
     *
     * @param location The address of the resource to obtain
     * @return The resource if it exists, otherwise null
     * @throws IOException If the resource may exist, but not could be obtained for some reason
     */
    @Nullable
    ExternalResource getResource(String location) throws IOException;

    /**
     * Obtain the SHA-1 checksum for the resource at the given location.
     *
     * Implementation is optional. If it is not feasible to obtain this without reading the
     * entire resource, implementations should return null.
     *
     * @param location The address of the resource to obtain the sha-1 of
     * @return The sha-1 if it can be cheaply obtained, otherwise null.
     */
    @Nullable
    HashValue getResourceSha1(String location);

    /**
     * Obtains only the metadata about the resource.
     *
     * If it is determined that the resource does not exist, this method should return null.
     *
     * If it is not possible to determine whether the resource exists or not, this method should
     * return a metadata instance with null/non value values (e.g. -1 for content length) to indicate
     * that the resource may indeed exist, but the metadata for it cannot be obtained.
     *
     * If the resource may exist but can't be accessed due to some configuration issue, the implementation
     * may either return an empty metadata object or throw an {@link IOException} to indicate a fatal condition.
     *
     * @param location The location of the resource to obtain the metadata for
     * @return The available metadata if possible, an “empty” metadata object if the
     *         metadata can't be reliably be obtained, null if the resource doesn't exist
     * @throws IOException If the resource may exist, but not could be obtained for some reason
     */
    @Nullable
    ExternalResourceMetaData getMetaData(String location) throws IOException;
    
}
