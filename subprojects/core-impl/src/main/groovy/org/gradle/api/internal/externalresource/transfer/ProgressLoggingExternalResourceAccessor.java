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

package org.gradle.api.internal.externalresource.transfer;

import org.apache.ivy.plugins.repository.Resource;
import org.gradle.api.Nullable;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.hash.HashValue;

import java.io.*;

public class ProgressLoggingExternalResourceAccessor extends AbstractProgressLoggingHandler implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, ProgressLoggerFactory progressLoggerFactory) {
        super(progressLoggerFactory);
        this.delegate = delegate;
    }

    public ExternalResource getResource(String location) throws IOException {
        ExternalResource resource = delegate.getResource(location);
        if (resource != null) {
            return new ProgressLoggingExternalResource(resource);
        } else {
            return null;
        }
    }

    @Nullable
    public HashValue getResourceSha1(String location) {
        return delegate.getResourceSha1(location);
    }

    @Nullable
    public ExternalResourceMetaData getMetaData(String location) throws IOException {
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
            ProgressLogger progressLogger = startProgress(String.format("Download %s", getName()), null);
            final ProgressLoggingOutputStream progressLoggingOutputStream = new ProgressLoggingOutputStream(outputStream, progressLogger, resource.getContentLength());
            try {
                resource.writeTo(progressLoggingOutputStream);
            } finally {
                progressLogger.completed();
            }
        }

        public Resource clone(String cloneName) {
            return resource.clone(cloneName);
        }

        public void close() throws IOException {
            resource.close();
        }

        @Nullable
        public ExternalResourceMetaData getMetaData() {
            return resource.getMetaData();
        }

        public String getName() {
            return resource.getName();
        }

        public long getLastModified() {
            return resource.getLastModified();
        }

        public long getContentLength() {
            return resource.getContentLength();
        }

        public boolean exists() {
            return resource.exists();
        }

        public boolean isLocal() {
            return resource.isLocal();
        }

        public InputStream openStream() throws IOException {
            return resource.openStream();
        }
    }

    private class ProgressLoggingOutputStream extends OutputStream {
        private long totalWritten;
        private OutputStream outputStream;
        private final ProgressLogger progressLogger;
        private long contentLength;

        public ProgressLoggingOutputStream(OutputStream outputStream, ProgressLogger progressLogger, long contentLength) {
            this.outputStream = outputStream;
            this.progressLogger = progressLogger;
            this.contentLength = contentLength;
        }

        @Override
        public void flush() throws IOException {
            outputStream.flush();
        }

        @Override
        public void close() throws IOException {
            outputStream.close();
        }

        @Override
        public void write(int b) throws IOException {
            outputStream.write(b);
            totalWritten++;
            doLogProgress();
        }

        public void write(byte b[], int off, int len) throws IOException {
            outputStream.write(b, off, len);
            totalWritten += len;
            doLogProgress();
        }

        private void doLogProgress() {
            logProgress(progressLogger, totalWritten, contentLength, "downloaded");
        }
    }
}
