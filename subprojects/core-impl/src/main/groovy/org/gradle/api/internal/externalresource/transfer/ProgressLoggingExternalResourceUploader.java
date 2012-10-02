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

import org.gradle.internal.Factory;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class ProgressLoggingExternalResourceUploader extends AbstractProgressLoggingHandler implements ExternalResourceUploader {
    private final ExternalResourceUploader delegate;

    public ProgressLoggingExternalResourceUploader(ExternalResourceUploader delegate, ProgressLoggerFactory progressLoggerFactory) {
        super(progressLoggerFactory);
        this.delegate = delegate;
    }

    public void upload(final Factory<InputStream> source, final Long contentLength, String destination) throws IOException {
        final ProgressLogger progressLogger = startProgress(String.format("Upload %s", destination), null);
        try {
            delegate.upload(new Factory<InputStream>() {
                public InputStream create() {
                    return new ProgressLoggingInputStream(source.create(), progressLogger, contentLength);
                }
            }, contentLength, destination);
        } finally {
            progressLogger.completed();
        }
    }

    private class ProgressLoggingInputStream extends InputStream {
        private long totalRead;
        private InputStream inputStream;
        private final ProgressLogger progressLogger;
        private long contentLength;


        public ProgressLoggingInputStream(InputStream inputStream, ProgressLogger progressLogger, long contentLength) {
            this.inputStream = inputStream;
            this.progressLogger = progressLogger;
            this.contentLength = contentLength;
        }

        @Override
        public void close() throws IOException {
            inputStream.close();
        }

        @Override
        public int read() throws IOException {
            int result = inputStream.read();
            if (result >= 0) {
                totalRead++;
                doLogProgress();
            }
            return result;
        }

        public int read(byte[] b, int off, int len) throws IOException {
            int read = inputStream.read(b, off, len);
            if (read > 0) {
                totalRead += read;
                doLogProgress();
            }
            return read;
        }

        private void doLogProgress() {
            logProgress(progressLogger, totalRead, contentLength, "uploaded");
        }
    }
}
