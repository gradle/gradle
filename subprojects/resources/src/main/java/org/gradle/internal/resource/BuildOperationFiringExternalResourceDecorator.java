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

package org.gradle.internal.resource;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

public class BuildOperationFiringExternalResourceDecorator implements ExternalResource {
    private final ExternalResourceName resourceName;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ExternalResource delegate;

    public BuildOperationFiringExternalResourceDecorator(ExternalResourceName resourceName, BuildOperationExecutor buildOperationExecutor, ExternalResource delegate) {
        this.resourceName = resourceName;
        this.buildOperationExecutor = buildOperationExecutor;
        this.delegate = delegate;
    }

    @Override
    public URI getURI() {
        return delegate.getURI();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceMetaData>() {
            @Override
            public ExternalResourceMetaData call(BuildOperationContext context) {
                ExternalResourceMetaData metaData = delegate.getMetaData();
                context.setResult(METADATA_RESULT);
                return metaData;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("Metadata of " + resourceName.getDisplayName())
                    .details(new MetadataOperationDetails(resourceName.getUri()));
            }
        });
    }

    @Nullable
    @Override
    public List<String> list() throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<List<String>>() {
            @Override
            public List<String> call(BuildOperationContext context) {
                List<String> list = delegate.list();
                context.setResult(LIST_RESULT);
                return list;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("List " + resourceName.getDisplayName())
                    .details(new ListOperationDetails(resourceName.getUri()));
            }
        });
    }

    @Override
    public ExternalResourceWriteResult put(final ReadableContent source) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceWriteResult>() {
            @Override
            public ExternalResourceWriteResult call(BuildOperationContext context) {
                final ExternalResourceWriteResult result = delegate.put(source);
                context.setResult(new ExternalResourceWriteBuildOperationType.Result() {
                    @Override
                    public long getBytesWritten() {
                        return result.getBytesWritten();
                    }
                });
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor
                    .displayName("Upload " + resourceName.getDisplayName())
                    .progressDisplayName(resourceName.getShortDisplayName())
                    .details(new PutOperationDetails(resourceName.getUri()));
            }
        });
    }

    @Override
    public ExternalResourceReadResult<Void> writeToIfPresent(final File destination) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<Void>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<Void> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.writeToIfPresent(destination));
            }
        });
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

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(final Transformer<? extends T, ? super InputStream> readAction) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<T>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<T> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.withContentIfPresent(readAction));
            }
        });
    }

    @Nullable
    @Override
    public <T> ExternalResourceReadResult<T> withContentIfPresent(final ContentAction<? extends T> readAction) throws ResourceException {
        return buildOperationExecutor.call(new CallableBuildOperation<ExternalResourceReadResult<T>>() {
            @Override
            public BuildOperationDescriptor.Builder description() {
                return createBuildOperationDetails();
            }

            @Override
            public ExternalResourceReadResult<T> call(BuildOperationContext buildOperationContext) {
                return result(buildOperationContext, delegate.withContentIfPresent(readAction));
            }
        });
    }

    private static <T> ExternalResourceReadResult<T> result(BuildOperationContext buildOperationContext, ExternalResourceReadResult<T> result) {
        buildOperationContext.setResult(new ReadOperationResult(result == null ? 0 : result.getBytesRead()));
        return result;
    }

    private BuildOperationDescriptor.Builder createBuildOperationDetails() {
        ExternalResourceReadBuildOperationType.Details operationDetails = new ReadOperationDetails(resourceName.getUri());
        return BuildOperationDescriptor
            .displayName("Download " + resourceName.getDisplayName())
            .progressDisplayName(resourceName.getShortDisplayName())
            .details(operationDetails);
    }

    private static class LocationDetails {
        private final URI location;

        private LocationDetails(URI location) {
            this.location = location;
        }

        public String getLocation() {
            return location.toASCIIString();
        }
    }

    private static class ReadOperationDetails extends LocationDetails implements ExternalResourceReadBuildOperationType.Details {
        private ReadOperationDetails(URI location) {
            super(location);
        }

        @Override
        public String toString() {
            return "ExternalResourceReadBuildOperationType.Details{location=" + getLocation() + ", " + '}';
        }
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

    private static class ListOperationDetails extends LocationDetails implements ExternalResourceListBuildOperationType.Details {
        private ListOperationDetails(URI location) {
            super(location);
        }

        @Override
        public String toString() {
            return "ExternalResourceListBuildOperationType.Details{location=" + getLocation() + ", " + '}';
        }
    }

    private final static ExternalResourceListBuildOperationType.Result LIST_RESULT = new ExternalResourceListBuildOperationType.Result() {
    };

    private static class PutOperationDetails extends LocationDetails implements ExternalResourceWriteBuildOperationType.Details {
        private PutOperationDetails(URI location) {
            super(location);
        }

        @Override
        public String toString() {
            return "ExternalResourceWriteBuildOperationType.Details{location=" + getLocation() + ", " + '}';
        }
    }

    private static class ReadOperationResult implements ExternalResourceReadBuildOperationType.Result {

        private final long bytesRead;

        private ReadOperationResult(long bytesRead) {
            this.bytesRead = bytesRead;
        }

        @Override
        public long getBytesRead() {
            return bytesRead;
        }

        @Override
        public String toString() {
            return "ExternalResourceReadBuildOperationType.Result{bytesRead=" + bytesRead + '}';
        }

    }
}
