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

import org.gradle.api.internal.artifacts.repositories.transport.http.HttpResourceCollection;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResource;
import org.gradle.util.hash.HashValue;

public class LocallyAvailableExternalResource extends LocalFileStandInExternalResource {

    private final LocallyAvailableResource locallyAvailableResource;

    public LocallyAvailableExternalResource(String source, LocallyAvailableResource locallyAvailableResource, HttpResourceCollection resourceCollection) {
        super(source, locallyAvailableResource.getOrigin(), resourceCollection);
        this.locallyAvailableResource = locallyAvailableResource;
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
