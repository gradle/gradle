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

package org.gradle.internal.resource.transfer;

import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.logging.ProgressLoggerFactory;

import java.io.*;
import java.net.URI;

public class ProgressLoggingExternalResourceAccessor extends AbstractProgressLoggingHandler implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, ProgressLoggerFactory progressLoggerFactory) {
        super(progressLoggerFactory);
        this.delegate = delegate;
    }

    public ExternalResource getResource(URI location) {
        ExternalResource resource = delegate.getResource(location);
        if (resource != null) {
            return new ProgressLoggingExternalResource(resource);
        } else {
            return null;
        }
    }

    @Nullable
    public ExternalResourceMetaData getMetaData(URI location) {
        return delegate.getMetaData(location);
    }

    private class ProgressLoggingExternalResource implements ExternalResource {
        private ExternalResource resource;

        private ProgressLoggingExternalResource(ExternalResource resource) {
            this.resource = resource;
        }

        /**
         * This redirect allows us to deprecate ExternalResource#writeto and replace usages later.
         */
        public void writeTo(File destination) throws IOException {
            FileOutputStream output = new FileOutputStream(destination);
            try {
                writeTo(output);
            } finally {
                output.close();
            }
        }

        public void writeTo(OutputStream outputStream) throws IOException {
            resource.writeTo(outputStream);
        }

        public void withContent(Action<? super InputStream> readAction) throws IOException {
            resource.withContent(readAction);
        }

        public <T> T withContent(Transformer<? extends T, ? super InputStream> readAction) throws IOException {
            return resource.withContent(readAction);
        }

        @Override
        public <T> T withContent(final ContentAction<? extends T> readAction) throws IOException {
            return resource.withContent(new ContentAction<T>() {
                @Override
                public T execute(InputStream inputStream, ExternalResourceMetaData metaData) throws IOException {
                    final ResourceOperation downloadOperation = createResourceOperation(resource.getName(), ResourceOperation.Type.download, getClass(), metaData.getContentLength());
                    try {
                        return readAction.execute(new ProgressLoggingInputStream(inputStream, downloadOperation), metaData);
                    } finally {
                        downloadOperation.completed();
                    }
                }
            });
        }

        public void close() throws IOException {
            resource.close();
        }

        @Nullable
        public ExternalResourceMetaData getMetaData() {
            return resource.getMetaData();
        }

        public URI getURI() {
            return resource.getURI();
        }

        public String getName() {
            return resource.getName();
        }

        public boolean isLocal() {
            return resource.isLocal();
        }

        public String toString(){
            return resource.toString();
        }
    }
}
