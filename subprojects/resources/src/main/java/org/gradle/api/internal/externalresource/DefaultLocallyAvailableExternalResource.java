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

package org.gradle.api.internal.externalresource;

import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.hash.HashValue;

public class DefaultLocallyAvailableExternalResource extends LocalFileStandInExternalResource implements LocallyAvailableExternalResource {
    private final LocallyAvailableResource locallyAvailableResource;

    public DefaultLocallyAvailableExternalResource(String source, LocallyAvailableResource locallyAvailableResource) {
        this(source, locallyAvailableResource, null);
    }

    public DefaultLocallyAvailableExternalResource(String source, LocallyAvailableResource locallyAvailableResource, ExternalResourceMetaData metaData) {
        super(source, locallyAvailableResource.getFile(), metaData);
        this.locallyAvailableResource = locallyAvailableResource;
    }

    @Override
    public String toString() {
        return locallyAvailableResource.toString();
    }

    public LocallyAvailableResource getLocalResource() {
        return locallyAvailableResource;
    }

    @Override
    public long getContentLength() {
        return locallyAvailableResource.getContentLength();
    }

    @Override
    protected HashValue getLocalFileSha1() {
        return locallyAvailableResource.getSha1();
    }
}
