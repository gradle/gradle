/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ExternalResourceReadResult;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.DownloadBuildOperationDetails;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class BuildOperationExternalResource implements ExternalResource {

    private final BuildOperationExecutor buildOperationExecutor;
    private final ExternalResource delegate;

    public BuildOperationExternalResource(BuildOperationExecutor buildOperationExecutor, ExternalResource delegate) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    @Override
    public URI getURI() {
        return delegate.getURI();
    }

    @Override
    public boolean isLocal() {
        return delegate.isLocal();
    }

    @Override
    public void close() {
        delegate.close();
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return delegate.getMetaData();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public ExternalResourceReadResult<Void> writeTo(final File destination) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<Void>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<Void> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.writeTo(destination));
            }
        });
    }

    @Override
    public ExternalResourceReadResult<Void> writeTo(final OutputStream destination) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<Void>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<Void> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.writeTo(destination));
            }
        });
    }

    @Override
    public ExternalResourceReadResult<Void> withContent(final Action<? super InputStream> readAction) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<Void>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<Void> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.withContent(readAction));
            }
        });
    }

    @Override
    public <T> ExternalResourceReadResult<T> withContent(final Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<T>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<T> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.withContent(readAction));
            }
        });
    }

    @Override
    public <T> ExternalResourceReadResult<T> withContent(final ContentAction<? extends T> readAction) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<T>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<T> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.withContent(readAction));
            }
        });
    }

    private static <T> ExternalResourceReadResult<T> result(BuildOperationContext buildOperationContext, ExternalResourceReadResult<T> result) {
        buildOperationContext.setResult(new DownloadBuildOperationDetails.Result(result.getReadContentLength()));
        return result;
    }

    private BuildOperationDescriptor.Builder createBuildOperationDetails() {
        ExternalResourceMetaData metaData = getMetaData();
        DownloadBuildOperationDetails downloadBuildOperationDetails = new DownloadBuildOperationDetails(metaData.getLocation(), metaData.getContentLength(), metaData.getContentType());
        return BuildOperationDescriptor
            .displayName("Download " + metaData.getLocation().toString())
            .details(downloadBuildOperationDetails);
    }
}
