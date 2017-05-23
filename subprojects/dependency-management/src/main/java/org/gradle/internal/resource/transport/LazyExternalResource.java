/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport;

import com.google.common.io.CountingInputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class LazyExternalResource implements ExternalResource {
    private final ExternalResourceName name;
    private final ExternalResourceAccessor accessor;
    // Should really be a parameter to the 'withContent' methods or baked into the accessor
    private final boolean revalidate;

    LazyExternalResource(ExternalResourceName name, ExternalResourceAccessor accessor, boolean revalidate) {
        this.name = name;
        this.accessor = accessor;
        this.revalidate = revalidate;
    }

    @Override
    public URI getURI() {
        return name.getUri();
    }

    @Override
    public String getDisplayName() {
        return name.getDisplayName();
    }

    @Nullable
    @Override
    public ExternalResourceReadResult<Void> writeToIfPresent(File destination) throws ResourceException {
        try {
            ExternalResourceReadResponse response = accessor.openResource(name.getUri(), revalidate);
            if (response == null) {
                return null;
            }
            try {
                CountingInputStream input = new CountingInputStream(response.openStream());
                try {
                    FileOutputStream output = new FileOutputStream(destination);
                    try {
                        IOUtils.copyLarge(input, output);
                        return ExternalResourceReadResult.of(input.getCount());
                    } finally {
                        output.close();
                    }
                } finally {
                    input.close();
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    @Override
    public ExternalResourceReadResult<Void> writeTo(File destination) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExternalResourceReadResult<Void> writeTo(OutputStream destination) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(Transformer<? extends T, ? super InputStream> transformer) throws ResourceException {
        try {
            ExternalResourceReadResponse response = accessor.openResource(name.getUri(), revalidate);
            if (response == null) {
                return null;
            }
            try {
                CountingInputStream input = new CountingInputStream(new BufferedInputStream(response.openStream()));
                try {
                    T value = transformer.transform(input);
                    return ExternalResourceReadResult.of(input.getCount(), value);
                } finally {
                    input.close();
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(name.getUri(), e);
        }
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(ContentAction<? extends T> readAction) throws ResourceException {
        try {
            ExternalResourceReadResponse response = accessor.openResource(name.getUri(), revalidate);
            if (response == null) {
                return null;
            }
            try {
                CountingInputStream stream = new CountingInputStream(new BufferedInputStream(response.openStream()));
                try {
                    T value = readAction.execute(stream, response.getMetaData());
                    return ExternalResourceReadResult.of(stream.getCount(), value);
                } finally {
                    stream.close();
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(name.getUri(), e);
        }
    }

    @Override
    public ExternalResourceReadResult<Void> withContent(Action<? super InputStream> readAction) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> ExternalResourceReadResult<T> withContent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> ExternalResourceReadResult<T> withContent(ContentAction<? extends T> readAction) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        throw new UnsupportedOperationException();
    }
}
