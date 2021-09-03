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

package org.gradle.internal.resource.transfer;

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public interface ExternalResourceAccessor {
    /**
     * Reads the resource at the given location.
     *
     * If the resource does not exist, this method should return null.
     *
     * If the resource may exist but can't be accessed due to some configuration issue, the implementation
     * must throw an {@link ResourceException} to indicate a fatal condition.
     *
     * @param location The address of the resource to obtain
     * @param revalidate The resource should be revalidated as part of the request
     * @param action The action to apply to the content of the resource.
     * @return The result of the action if the resource exists, or null if the resource does not exist.
     * @throws ResourceException If the resource may exist, but not could be obtained for some reason.
     */
    @Nullable
    <T> T withContent(URI location, boolean revalidate, ContentAndMetadataAction<T> action) throws ResourceException;

    /**
     * Reads the resource at the given location.
     *
     * If the resource does not exist, this method should return null.
     *
     * If the resource may exist but can't be accessed due to some configuration issue, the implementation
     * must throw an {@link ResourceException} to indicate a fatal condition.
     *
     * @param location The address of the resource to obtain
     * @param revalidate The resource should be revalidated as part of the request
     * @param action The action to apply to the content of the resource.
     * @return The result of the action if the resource exists, or null if the resource does not exist.
     * @throws ResourceException If the resource may exist, but not could be obtained for some reason.
     */
    @Nullable
    default <T> T withContent(URI location, boolean revalidate, ContentAction<T> action) throws ResourceException {
        return withContent(location, revalidate, (metaData, inputStream) -> action.execute(inputStream));
    }

    /**
     * Obtains only the metadata about the resource.
     *
     * If it is determined that the resource does not exist, this method should return null.
     *
     * If the resource may exist but can't be accessed due to some configuration issue, the implementation
     * must throw an {@link ResourceException} to indicate a fatal condition.
     *
     * @param location The location of the resource to obtain the metadata for
     * @param revalidate The resource should be revalidated as part of the request
     * @return The available metadata, null if the resource doesn't exist
     * @throws ResourceException If the resource may exist, but not could be obtained for some reason
     */
    @Nullable
    ExternalResourceMetaData getMetaData(URI location, boolean revalidate) throws ResourceException;

    interface ContentAction<T> {
        T execute(InputStream inputStream) throws IOException;
    }

    interface ContentAndMetadataAction<T> {
        T execute(ExternalResourceMetaData metaData, InputStream inputStream) throws IOException;
    }
}
