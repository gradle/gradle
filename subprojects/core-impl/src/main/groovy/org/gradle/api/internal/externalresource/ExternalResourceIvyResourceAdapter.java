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

import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.util.CopyProgressListener;
import org.gradle.api.internal.externalresource.metadata.DefaultExternalResourceMetaData;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class ExternalResourceIvyResourceAdapter implements ExternalResource {

    private final Resource delegate;

    public ExternalResourceIvyResourceAdapter(Resource delegate) {
        this.delegate = delegate;
    }

    public void writeTo(File destination, CopyProgressListener progress) throws IOException {
        throwUnsupported();
    }

    public void close() throws IOException {
        // noop
    }

    private void throwUnsupported() {
        throw new UnsupportedOperationException("ExternalResourceIvyResourceAdapter does not support methods added by ExternalResource");
    }

    public String getName() {
        return delegate.getName();
    }

    public long getLastModified() {
        return delegate.getLastModified();
    }

    public long getContentLength() {
        return delegate.getContentLength();
    }

    public boolean exists() {
        return delegate.exists();
    }

    public boolean isLocal() {
        return delegate.isLocal();
    }

    public Resource clone(String cloneName) {
        return delegate.clone(cloneName);
    }

    public InputStream openStream() throws IOException {
        return delegate.openStream();
    }

    public ExternalResourceMetaData getMetaData() {
        return new DefaultExternalResourceMetaData(delegate.getName(), getLastModified(), getContentLength(), null, null);
    }
}
