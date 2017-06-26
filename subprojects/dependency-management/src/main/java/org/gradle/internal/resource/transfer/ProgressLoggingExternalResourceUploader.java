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

import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class ProgressLoggingExternalResourceUploader extends AbstractProgressLoggingHandler implements ExternalResourceUploader {
    private final ExternalResourceUploader delegate;

    public ProgressLoggingExternalResourceUploader(ExternalResourceUploader delegate, ProgressLoggerFactory progressLoggerFactory) {
        super(progressLoggerFactory);
        this.delegate = delegate;
    }

    @Override
    public void upload(final ReadableContent resource, URI destination) throws IOException {
        final ResourceOperation uploadOperation = createResourceOperation(destination, ResourceOperation.Type.upload, getClass(), resource.getContentLength());

        try {
            delegate.upload(new ProgressLoggingReadableContent(resource, uploadOperation), destination);
        } finally {
            uploadOperation.completed();
        }
    }

    private class ProgressLoggingReadableContent implements ReadableContent {
        private final ReadableContent delegate;
        private final ResourceOperation uploadOperation;

        private ProgressLoggingReadableContent(ReadableContent delegate, ResourceOperation uploadOperation) {
            this.delegate = delegate;
            this.uploadOperation = uploadOperation;
        }

        public InputStream open() {
            return new ProgressLoggingInputStream(delegate.open(), uploadOperation);
        }

        public long getContentLength() {
            return delegate.getContentLength();
        }
    }
}
