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

import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.Transformer;

import java.io.*;

public abstract class AbstractExternalResource implements ExternalResource {
    /**
     * Opens an unbuffered input stream to read the contents of this resource.
     */
    protected abstract InputStream openStream() throws IOException;

    private InputStream openBuffered() {
        try {
            return new BufferedInputStream(openStream());
        } catch (FileNotFoundException e) {
            throw ResourceExceptions.getMissing(getURI(), e);
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

    public void writeTo(File destination) {
        try {
            FileOutputStream output = new FileOutputStream(destination);
            try {
                writeTo(output);
            } finally {
                output.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    public void writeTo(OutputStream output) {
        try {
            InputStream input = openStream();
            try {
                IOUtils.copyLarge(input, output);
            } finally {
                input.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(getURI(), e);
        }
    }

    public void withContent(Action<? super InputStream> readAction) {
        InputStream input = openBuffered();
        try {
            readAction.execute(input);
        } finally {
            close(input);
        }
    }

    public <T> T withContent(Transformer<? extends T, ? super InputStream> readAction) {
        InputStream input = openBuffered();
        try {
            return readAction.transform(input);
        } finally {
            close(input);
        }
    }

    @Override
    public <T> T withContent(ContentAction<? extends T> readAction) {
        InputStream input = openBuffered();
        try {
            try {
                return readAction.execute(input, getMetaData());
            } catch (IOException e) {
                throw ResourceExceptions.getFailed(getURI(), e);
            }
        } finally {
            close(input);
        }
    }

    public void close() {
    }
}
