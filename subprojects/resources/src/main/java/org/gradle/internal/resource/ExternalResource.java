/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.*;
import java.net.URI;

/**
 * This will be merged with {@link Resource}.
 */
public interface ExternalResource extends Resource, Closeable {
    /**
     * Get the URI of the resource.
     */
    URI getURI();

    /**
     * Is this resource local to this host, i.e. is it on the file system?
     *
     * @return <code>boolean</code> value indicating if the resource is local.
     */
    boolean isLocal();

    /**
     * Copies the contents of this resource to the given file.
     *
     * @throws ResourceException on failure to copy the content.
     */
    void writeTo(File destination) throws ResourceException;

    /**
     * Copies the binary contents of this resource to the given stream. Does not close the provided stream.
     *
     * @throws ResourceException on failure to copy the content.
     */
    void writeTo(OutputStream destination) throws ResourceException;

    /**
     * Executes the given action against the binary contents of this resource.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    void withContent(Action<? super InputStream> readAction) throws ResourceException;

    /**
     * Executes the given action against the binary contents of this resource.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    <T> T withContent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException;

    /**
     * Executes the given action against the binary contents and meta-data of this resource.
     * Generally, this method will be less efficient than one of the other {@code withContent} methods that do
     * not provide the meta-data, as additional requests may need to be made to obtain the meta-data.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    <T> T withContent(ContentAction<? extends T> readAction) throws ResourceException;

    void close() throws ResourceException;

    /**
     * Returns the meta-data for this resource.
     */
    ExternalResourceMetaData getMetaData();

    interface ContentAction<T> {
        T execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException;
    }
}
