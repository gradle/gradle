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

import com.google.common.io.CountingInputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class AbstractExternalResource implements ExternalResource {
    /**
     * Opens an unbuffered input stream to read the contents of this resource.
     * @return null if the resource does not exist.
     */
    @Nullable
    protected abstract InputStream openStream() throws IOException;

    private CountingInputStream openUnbufferedIfPresent() {
        try {
            InputStream input = openStream();
            if (input == null) {
                return null;
            }
            return new CountingInputStream(input);
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    private CountingInputStream openUnbuffered() {
        try {
            InputStream input = openStream();
            if (input == null) {
                throw ResourceExceptions.getMissing(getURI());
            }
            return new CountingInputStream(input);
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    private CountingInputStream openBuffered() {
        try {
            InputStream input = openStream();
            if (input == null) {
                throw ResourceExceptions.getMissing(getURI());
            }
            return new CountingInputStream(new BufferedInputStream(input));
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    private void close(InputStream input) {
        try {
            input.close();
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    @Override
    public String getDisplayName() {
        return getURI().toString();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public ExternalResourceReadResult<Void> writeToIfPresent(File destination) throws ResourceException {
        try {
            CountingInputStream input = openUnbufferedIfPresent();
            if (input == null) {
                return null;
            }
            try {
                FileOutputStream output = new FileOutputStream(destination);
                try {
                    IOUtils.copyLarge(input, output);
                } finally {
                    output.close();
                }
            } finally {
                input.close();
            }
            return ExternalResourceReadResult.of(input.getCount());
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    public ExternalResourceReadResult<Void> writeTo(File destination) {
        ExternalResourceReadResult<Void> result = writeToIfPresent(destination);
        if (result == null) {
            throw ResourceExceptions.getMissing(getURI());
        }
        return result;
    }

    public ExternalResourceReadResult<Void> writeTo(OutputStream output) {
        try {
            CountingInputStream input = openUnbuffered();
            try {
                IOUtils.copyLarge(input, output);
            } finally {
                input.close();
            }
            return ExternalResourceReadResult.of(input.getCount());
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    public ExternalResourceReadResult<Void> withContent(Action<? super InputStream> readAction) {
        CountingInputStream input = openBuffered();
        try {
            readAction.execute(input);
        } finally {
            close(input);
        }

        return ExternalResourceReadResult.of(input.getCount());
    }

    public <T> ExternalResourceReadResult<T> withContent(Transformer<? extends T, ? super InputStream> readAction) {
        CountingInputStream input = openBuffered();
        try {
            T result = readAction.transform(input);
            return ExternalResourceReadResult.of(input.getCount(), result);
        } finally {
            close(input);
        }
    }

    @Override
    public <T> ExternalResourceReadResult<T> withContent(ContentAction<? extends T> readAction) {
        CountingInputStream input = openBuffered();
        try {
            try {
                T resourceReadResult = readAction.execute(input, getMetaData());
                return ExternalResourceReadResult.of(input.getCount(), resourceReadResult);
            } catch (IOException e) {
                throw ResourceExceptions.getFailed(getURI(), e);
            }
        } finally {
            close(input);
        }
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(ContentAction<? extends T> readAction) throws ResourceException {
        throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
        throw new UnsupportedOperationException();
    }
}
