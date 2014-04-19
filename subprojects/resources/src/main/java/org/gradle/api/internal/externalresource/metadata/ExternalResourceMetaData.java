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

package org.gradle.api.internal.externalresource.metadata;

import org.gradle.api.Nullable;
import org.gradle.internal.hash.HashValue;

import java.util.Date;

public interface ExternalResourceMetaData {

    String getLocation();

    @Nullable
    Date getLastModified();

    long getContentLength();

    /**
     * Some kind of opaque checksum that was advertised by the remote “server”.
     * 
     * For HTTP this is likely the value of the ETag header but it may be any kind of opaque checksum.
     * 
     * @return The entity tag, or null if there was no advertised or suitable etag.
     */
    @Nullable
    String getEtag();

    /**
     * The advertised sha-1 of the external resource.
     *
     * This should only be collected if it is very cheap to do so. For example, some HTTP servers send an
     * “X-Checksum-Sha1” that makes the sha1 available cheaply. In this case it makes sense to advertise this as metadata here.
     *
     * @return The sha1, or null if it's unknown.
     */
    @Nullable
    HashValue getSha1();

}
