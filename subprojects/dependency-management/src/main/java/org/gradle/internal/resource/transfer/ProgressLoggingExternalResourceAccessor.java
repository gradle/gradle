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

import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class ProgressLoggingExternalResourceAccessor extends AbstractProgressLoggingHandler implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, ProgressLoggerFactory progressLoggerFactory, BuildOperationExecutor buildOperationExecutor) {
        super(progressLoggerFactory);
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public ExternalResourceReadResponse openResource(final URI location, final boolean revalidate) {
        ExternalResourceReadResponse resource = delegate.openResource(location, revalidate);
        if (resource != null) {
            return new ProgressLoggingExternalResource(location, resource);
        } else {
            return null;
        }
    }

    @Nullable
    public ExternalResourceMetaData getMetaData(URI location, boolean revalidate) {
        return delegate.getMetaData(location, revalidate);
    }

    @Override
    public <T> T withResource(final URI location, boolean revalidate, final Transformer<T, ExternalResource> transformer) throws ResourceException {
        BuildOperationDetails buildOperationDetails = createOperationDetailsDetails(location, revalidate);
        return buildOperationExecutor.run(buildOperationDetails, new Transformer<T, BuildOperationContext>() {
            @Override
            public T transform(BuildOperationContext context) {
                return delegate.withResource(location, false, new Transformer<T, ExternalResource>() {
                    @Override
                    public T transform(ExternalResource readResponse) {
                        return transformer.transform(readResponse);
                    }
                });
            }
        });
    }

    private BuildOperationDetails createOperationDetailsDetails(URI location, boolean revalidate) {
        ExternalResourceMetaData metaData = delegate.getMetaData(location, revalidate);
        DownloadBuildOperationDescriptor downloadBuildOperationDescriptor = new DownloadBuildOperationDescriptor(metaData.getLocation(), metaData.getContentLength(), metaData.getContentType());
        return BuildOperationDetails.displayName("Download " + metaData.getLocation().toString()).parent(buildOperationExecutor.getCurrentOperation()).operationDescriptor(downloadBuildOperationDescriptor).build();
    }

    private class ProgressLoggingExternalResource implements ExternalResourceReadResponse {
        private final ExternalResourceReadResponse resource;
        private final ResourceOperation downloadOperation;

        private ProgressLoggingExternalResource(URI location, ExternalResourceReadResponse resource) {
            this.resource = resource;
            downloadOperation = createResourceOperation(location.toString(), ResourceOperation.Type.download, getClass(), resource.getMetaData().getContentLength());
        }

        @Override
        public InputStream openStream() throws IOException {
            return new ProgressLoggingInputStream(resource.openStream(), downloadOperation);
        }

        public void close() throws IOException {
            try {
                resource.close();
            } finally {
                downloadOperation.completed();
            }
        }

        @Nullable
        public ExternalResourceMetaData getMetaData() {
            return resource.getMetaData();
        }

        public boolean isLocal() {
            return resource.isLocal();
        }

        public String toString() {
            return resource.toString();
        }
    }

}
