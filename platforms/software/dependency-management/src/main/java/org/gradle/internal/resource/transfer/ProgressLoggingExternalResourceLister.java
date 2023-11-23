/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resource.ExternalResourceListBuildOperationType;
import org.gradle.internal.resource.ExternalResourceName;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;

public class ProgressLoggingExternalResourceLister extends AbstractProgressLoggingHandler implements ExternalResourceLister {
    private final ExternalResourceLister delegate;
    private final BuildOperationExecutor buildOperationExecutor;

    public ProgressLoggingExternalResourceLister(ExternalResourceLister delegate, BuildOperationExecutor buildOperationExecutor) {
        this.delegate = delegate;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Nullable
    @Override
    public List<String> list(ExternalResourceName parent) throws ResourceException {
        return buildOperationExecutor.call(new ListOperation(parent));
    }

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

    private class ListOperation implements CallableBuildOperation<List<String>> {
        private final ExternalResourceName parent;

        public ListOperation(ExternalResourceName parent) {
            this.parent = parent;
        }

        @Override
        public List<String> call(BuildOperationContext context) {
            try {
                return delegate.list(parent);
            } finally {
                context.setResult(LIST_RESULT);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor
                .displayName("List " + parent.getUri())
                .details(new ListOperationDetails(parent.getUri()));
        }
    }
}
