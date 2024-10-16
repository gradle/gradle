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
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType;
import org.gradle.internal.resource.ExternalResourceReadMetadataBuildOperationType;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

public class ProgressLoggingExternalResourceAccessor extends AbstractProgressLoggingHandler implements ExternalResourceAccessor {
    private final ExternalResourceAccessor delegate;
    private final BuildOperationRunner buildOperationRunner;

    public ProgressLoggingExternalResourceAccessor(ExternalResourceAccessor delegate, BuildOperationRunner buildOperationRunner) {
        this.delegate = delegate;
        this.buildOperationRunner = buildOperationRunner;
    }

    @Nullable
    @Override
    public <T> T withContent(ExternalResourceName location, boolean revalidate, @Nullable File partPosition, ExternalResource.ContentAndMetadataAction<T> action) throws ResourceException {
        return buildOperationRunner.call(new DownloadOperation<>(location, revalidate, partPosition, action));
    }

    @Override
    @Nullable
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) {
        return buildOperationRunner.call(new MetadataOperation(location, revalidate));
    }

    private BuildOperationDescriptor.Builder createBuildOperationDetails(ExternalResourceName resourceName) {
        ExternalResourceReadBuildOperationType.Details operationDetails = new ReadOperationDetails(resourceName.getUri());
        return BuildOperationDescriptor
            .displayName("Download " + resourceName.getUri())
            .progressDisplayName(resourceName.getShortDisplayName())
            .details(operationDetails);
    }

    private static class MetadataOperationDetails extends LocationDetails implements ExternalResourceReadMetadataBuildOperationType.Details {
        private MetadataOperationDetails(URI location) {
            super(location);
        }

        @Override
        public String toString() {
            return "ExternalResourceReadMetadataBuildOperationType.Details{location=" + getLocation() + ", " + '}';
        }
    }

    private final static ExternalResourceReadMetadataBuildOperationType.Result METADATA_RESULT = new ExternalResourceReadMetadataBuildOperationType.Result() {
    };

    private static class ReadOperationDetails extends LocationDetails implements ExternalResourceReadBuildOperationType.Details {
        private ReadOperationDetails(URI location) {
            super(location);
        }

        @Override
        public String toString() {
            return "ExternalResourceReadBuildOperationType.Details{location=" + getLocation() + ", " + '}';
        }
    }

    private static class ReadOperationResult implements ExternalResourceReadBuildOperationType.Result {

        private final long bytesRead;
        private final boolean missing;

        private ReadOperationResult(long bytesRead, boolean missing) {
            this.bytesRead = bytesRead;
            this.missing = missing;
        }

        @Override
        public long getBytesRead() {
            return bytesRead;
        }

        @Override
        public boolean isMissing() {
            return missing;
        }

        @Override
        public String toString() {
            return "ExternalResourceReadBuildOperationType.Result{" +
                "bytesRead=" + bytesRead +
                ", missing=" + missing +
                '}';
        }
    }

    private class DownloadOperation<T> implements CallableBuildOperation<T> {
        private final ExternalResourceName location;
        private final boolean revalidate;
        private final File partPosition;
        private final ExternalResource.ContentAndMetadataAction<T> action;

        public DownloadOperation(ExternalResourceName location, boolean revalidate, @Nullable File partPosition, ExternalResource.ContentAndMetadataAction<T> action) {
            this.location = location;
            this.revalidate = revalidate;
            this.partPosition = partPosition;
            this.action = action;
        }

        @Override
        public T call(BuildOperationContext context) {
            ResourceOperation downloadOperation = createResourceOperation(context, ResourceOperation.Type.download);
            AtomicReference<ExternalResourceMetaData> metadata = new AtomicReference<>();
            try {
                return delegate.withContent(location, revalidate, partPosition, (inputStream, metaData) -> {
                    downloadOperation.setContentLength(metaData.getContentLength());
                    metadata.set(metaData);
                    if (metaData.wasMissing()) {
                        context.failed(ResourceExceptions.getMissing(metaData.getLocation()));
                        return null;
                    }
                    ProgressLoggingInputStream stream = new ProgressLoggingInputStream(inputStream, downloadOperation);
                    return action.execute(stream, metaData);
                });
            } finally {
                ExternalResourceMetaData externalResourceMetaData = metadata.get();
                context.setResult(new ReadOperationResult(
                    downloadOperation.getTotalProcessedBytes(),
                    externalResourceMetaData != null && externalResourceMetaData.wasMissing()
                ));
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return createBuildOperationDetails(location);
        }
    }

    private class MetadataOperation implements CallableBuildOperation<ExternalResourceMetaData> {
        private final ExternalResourceName location;
        private final boolean revalidate;

        public MetadataOperation(ExternalResourceName location, boolean revalidate) {
            this.location = location;
            this.revalidate = revalidate;
        }

        @Override
        public ExternalResourceMetaData call(BuildOperationContext context) {
            try {
                return delegate.getMetaData(location, revalidate);
            } finally {
                context.setResult(METADATA_RESULT);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor
                .displayName("Metadata of " + location.getDisplayName())
                .details(new MetadataOperationDetails(location.getUri()));
        }
    }
}
