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
import org.apache.ivy.util.CopyProgressListener;
import org.gradle.api.Nullable;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.internal.UncheckedException;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.util.hash.HashValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProgressLoggingExternalResourceAccessor implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;
    private ProgressLoggerFactory progressLoggerFactory;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, ProgressLoggerFactory progressLoggerFactory) {
        this.delegate = delegate;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public ExternalResource getResource(String location) throws IOException {
        ExternalResource resource = delegate.getResource(location);
        return new ProgressLoggingExternalResource(resource, progressLoggerFactory);
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
        private ProgressLoggerFactory progressLoggerFactory;

        public ProgressLoggingExternalResource(ExternalResource resource, ProgressLoggerFactory progressLoggerFactory) {
            this.resource = resource;
            this.progressLoggerFactory = progressLoggerFactory;
        }

        public void writeTo(File destination, CopyProgressListener lister) throws IOException {
            resource.writeTo(destination, lister);
        }

        public void writeTo(OutputStream outputStream, CopyProgressListener lister) throws IOException {  //get rid of CopyProgress Logger
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(ProgressLoggingExternalResource.class);
            progressLogger.setDescription(String.format("Download %s", getName()));
            progressLogger.setShortDescription(String.format("Download %s", getName()));
            progressLogger.started();
            final ProgressLoggingOutputStream progressLoggingOutputStream = new ProgressLoggingOutputStream(outputStream, progressLogger, resource.getContentLength());
            try {
                resource.writeTo(progressLoggingOutputStream, lister);
            } catch (IOException e) {
                progressLogger.completed(String.format("Failed to write %s to %s.", getName()));
                throw e;
            } catch (Exception e) {
                progressLogger.completed(String.format("Failed to write %s to %s.", getName()));
                throw UncheckedException.throwAsUncheckedException(e);
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
        public void write(int b) throws IOException {
            outputStream.write(b);
        }

        public void write(byte b[], int off, int len) throws IOException {
            outputStream.write(b, off, len);
            totalWritten += len;
            if (progressLogger != null) {
                progressLogger.progress(String.format("%s/%s downloaded", getLengthText(totalWritten), getLengthText()));
            }
        }

        private String getLengthText() {
            return getLengthText(contentLength != 0 ? contentLength : null);
        }

        private String getLengthText(Long bytes) {
            if (bytes == null) {
                return "unknown size";
            }
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1048576) {
                return (bytes / 1024) + " KB";
            } else {
                return String.format("%.2f MB", bytes / 1048576.0);
            }
        }
    }
}
