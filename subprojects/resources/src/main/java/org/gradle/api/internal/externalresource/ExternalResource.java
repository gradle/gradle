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
package org.gradle.api.internal.externalresource;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface ExternalResource {
    /**
     * Get the name of the resource.
     */
    public String getName();

    /**
     * Get the date the resource was last modified
     *
     * @return A <code>long</code> value representing the time the file was last modified,
     *         measured in milliseconds since the epoch (00:00:00 GMT, January 1, 1970), or
     *         <code>0L</code> if the file does not exist or if an I/O error occurs.
     */
    public long getLastModified();

    /**
     * Get the resource size
     *
     * @return a <code>long</code> value representing the size of the resource in bytes.
     */
    public long getContentLength();

    /**
     * Determine if the resource is available.
     * </p>
     * Note that this method only checks for availability, not for actual existence.
     *
     * @return <code>boolean</code> value indicating if the resource is available.
     */
    public boolean exists();

    /**
     * Is this resource local to this host, i.e. is it on the file system?
     *
     * @return <code>boolean</code> value indicating if the resource is local.
     */
    public boolean isLocal();

    /**
     * Copies the contents of this resource to the given file.
     */
    void writeTo(File destination) throws IOException;

    /**
     * Copies the contents of this resource to the given stream. Does not close the stream.
     */
    void writeTo(OutputStream destination) throws IOException;

    /**
     * Executes the given action against the contents of this resource.
     */
    void withContent(Action<? super InputStream> readAction) throws IOException;

    /**
     * Executes the given action against the contents of this resource.
     */
    <T> T withContent(Transformer<? extends T, ? super InputStream> readAction) throws IOException;

    void close() throws IOException;

    ExternalResourceMetaData getMetaData();
}
