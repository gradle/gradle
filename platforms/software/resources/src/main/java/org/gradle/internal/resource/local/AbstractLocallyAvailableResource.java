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
package org.gradle.internal.resource.local;

import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;

public abstract class AbstractLocallyAvailableResource implements LocallyAvailableResource {
    private Factory<HashCode> factory;
    // Calculated on demand
    private HashCode sha1;
    private Long contentLength;
    private Long lastModified;

    protected AbstractLocallyAvailableResource(Factory<HashCode> factory) {
        this.factory = factory;
    }

    protected AbstractLocallyAvailableResource(HashCode sha1) {
        this.sha1 = sha1;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return getFile().getPath();
    }

    @Override
    public HashCode getSha1() {
        if (sha1 == null) {
            sha1 = factory.create();
        }
        return sha1;
    }

    @Override
    public long getContentLength() {
        if (contentLength == null) {
            contentLength = getFile().length();
        }
        return contentLength;
    }

    @Override
    public long getLastModified() {
        if (lastModified == null) {
            lastModified = getFile().lastModified();
        }
        return lastModified;
    }

}
