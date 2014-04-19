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

import java.io.IOException;
import java.io.InputStream;

public class MetaDataOnlyExternalResource extends AbstractExternalResource {

    private final ExternalResourceMetaData metaData;
    private final String source;
    private final boolean local;

    public MetaDataOnlyExternalResource(String source, ExternalResourceMetaData metaData) {
        this(source, metaData, false);
    }

    public MetaDataOnlyExternalResource(String source, ExternalResourceMetaData metaData, boolean local) {
        this.source = source;
        this.metaData = metaData;
        this.local = local;
    }

    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }

    @Override
    public String toString() {
        return "MetaDataOnlyResource: " + getName();
    }

    public String getName() {
        return source;
    }

    public boolean exists() {
        return true;
    }

    public long getLastModified() {
        return -1;
    }

    public long getContentLength() {
        return -1;
    }

    public boolean isLocal() {
        return local;
    }

    public InputStream openStream() throws IOException {
        throw new UnsupportedOperationException();
    }

}
