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

import org.gradle.api.resources.ResourceException;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.net.URI;

public class ProgressLoggingExternalResourceAccessor extends AbstractProgressLoggingHandler implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, ProgressLoggerFactory progressLoggerFactory) {
        super(progressLoggerFactory);
        this.delegate = delegate;
    }

    @Nullable
    @Override
    public <T> T withContent(URI location, boolean revalidate, ContentAndMetadataAction<T> action) throws ResourceException {
        return delegate.withContent(location, revalidate, (metaData, inputStream) -> {
            ResourceOperation downloadOperation = createResourceOperation(location, ResourceOperation.Type.download, getClass(), metaData.getContentLength());
            ProgressLoggingInputStream stream = new ProgressLoggingInputStream(inputStream, downloadOperation);
            try {
                return action.execute(metaData, stream);
            } finally {
                downloadOperation.completed();
            }
        });
    }

    @Override
    @Nullable
    public ExternalResourceMetaData getMetaData(URI location, boolean revalidate) {
        return delegate.getMetaData(location, revalidate);
    }
}
