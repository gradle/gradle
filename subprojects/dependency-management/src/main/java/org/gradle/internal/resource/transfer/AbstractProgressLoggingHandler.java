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

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class AbstractProgressLoggingHandler {
    protected final ProgressLoggerFactory progressLoggerFactory;

    public AbstractProgressLoggingHandler(ProgressLoggerFactory progressLoggerFactory) {
        this.progressLoggerFactory = progressLoggerFactory;
    }

    protected ResourceOperation createResourceOperation(URI resource, ResourceOperation.Type operationType, Class<?> loggingClazz) {
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(loggingClazz != null ? loggingClazz : getClass());
        String description = createDescription(operationType, resource);
        progressLogger.setDescription(description);
        progressLogger.started();
        return new ResourceOperation(progressLogger, operationType);
    }

    private String createDescription(ResourceOperation.Type operationType, URI resource) {
        return operationType.getCapitalized() + " " + resource;
    }

    protected static class LocationDetails {
        private final URI location;

        LocationDetails(URI location) {
            this.location = location;
        }

        public String getLocation() {
            return location.toASCIIString();
        }
    }

    protected static class ProgressLoggingInputStream extends InputStream {
        private final InputStream inputStream;
        private final ResourceOperation resourceOperation;

        public ProgressLoggingInputStream(InputStream inputStream, ResourceOperation resourceOperation) {
            this.inputStream = inputStream;
            this.resourceOperation = resourceOperation;
        }

        public long getBytesRead() {
            return resourceOperation.getTotalProcessedBytes();
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }

        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException("Reading from a remote resource should be buffered.");
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int read = inputStream.read(b, off, len);
            if (read > 0) {
                doLogProgress(read);
            }
            return read;
        }

        private void doLogProgress(long numberOfBytes) {
            resourceOperation.logProcessedBytes(numberOfBytes);
        }
    }
}
