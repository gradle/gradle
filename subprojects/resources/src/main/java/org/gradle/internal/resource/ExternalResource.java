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

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

/**
 * Represents a binary resource and provides access to the content and meta-data of the resource. The resource may or may not exist, and may change over time.
 */
public interface ExternalResource extends Resource {
    /**
     * Get the URI of the resource.
     */
    URI getURI();

    /**
     * Copies the contents of this resource to the given file.
     *
     * @throws ResourceException on failure to copy the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    ExternalResourceReadResult<Void> writeTo(File destination) throws ResourceException;

    /**
     * Copies the contents of this resource to the given file, if the resource exists.
     *
     * @throws ResourceException on failure to copy the content.
     * @return null if this resource does not exist.
     */
    @Nullable
    ExternalResourceReadResult<Void> writeToIfPresent(File destination) throws ResourceException;

    /**
     * Copies the binary contents of this resource to the given stream. Does not close the provided stream.
     *
     * @throws ResourceException on failure to copy the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    ExternalResourceReadResult<Void> writeTo(OutputStream destination) throws ResourceException;

    /**
     * Executes the given action against the binary contents of this resource.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    ExternalResourceReadResult<Void> withContent(Action<? super InputStream> readAction) throws ResourceException;

    /**
     * Executes the given action against the binary contents of this resource.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    <T> ExternalResourceReadResult<T> withContent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException;

    /**
     * Executes the given action against the binary contents of this resource, if the resource exists.
     *
     * @throws ResourceException on failure to read the content.
     * @return null if the resource does not exist.
     */
    @Nullable
    <T> ExternalResourceReadResult<T> withContentIfPresent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException;

    /**
     * Executes the given action against the binary contents and meta-data of this resource.
     * Generally, this method will be less efficient than one of the other {@code withContent} methods that do
     * not provide the meta-data, as additional requests may need to be made to obtain the meta-data.
     *
     * @throws ResourceException on failure to read the content.
     * @throws org.gradle.api.resources.MissingResourceException when the resource does not exist
     */
    <T> ExternalResourceReadResult<T> withContent(ContentAction<? extends T> readAction) throws ResourceException;

    /**
     * Executes the given action against the binary contents and meta-data of this resource.
     * Generally, this method will be less efficient than one of the other {@code withContent} methods that do
     * not provide the meta-data, as additional requests may need to be made to obtain the meta-data.
     *
     * @throws ResourceException on failure to read the content.
     * @return null if the resource does not exist.
     */
    @Nullable
    <T> ExternalResourceReadResult<T> withContentIfPresent(ContentAction<? extends T> readAction) throws ResourceException;

    /**
     * Copies the given content to this resource.
     *
     * @param source The local resource to be transferred.
     * @throws ResourceException On failure to write the content.
     */
    ExternalResourceWriteResult put(ReadableContent source) throws ResourceException;

    /**
     * Return a listing of child resources names.
     *
     * @return A listing of the direct children of the given parent. Returns null when the parent resource does not exist.
     * @throws ResourceException On listing failure.
     */
    @Nullable
    List<String> list() throws ResourceException;

    /**
     * Returns the meta-data for this resource, if the resource exists.
     * @return null when the resource does not exist.
     */
    @Nullable
    ExternalResourceMetaData getMetaData();

    interface ContentAction<T> {
        T execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException;
    }
}
