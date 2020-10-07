/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.operations;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filters build operations and re-writes parents for their descendants as needed.
 */
public class FilteringBuildOperationBuildOperationListener implements BuildOperationListener {
    private final Map<OperationIdentifier, Op> mapping = new ConcurrentHashMap<OperationIdentifier, Op>();

    private final BuildOperationListener delegate;
    private final Filter filter;

    public FilteringBuildOperationBuildOperationListener(BuildOperationListener delegate, Filter filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        OperationIdentifier parentId = buildOperation.getParentId();
        OperationIdentifier mappedParentId;
        OperationIdentifier effectiveParentId;
        if (parentId != null) {
            Op mapping = this.mapping.get(parentId);
            if (mapping == null) {
                effectiveParentId = parentId;
                mappedParentId = null;
            } else {
                mappedParentId = mapping.getMappedId();
                effectiveParentId = mappedParentId;
            }
        } else {
            mappedParentId = null;
            effectiveParentId = null;
        }
        Op op;
        if (filter.shouldForward(buildOperation)) {
            BuildOperationDescriptor forwardedDescriptor = buildOperation.withParentId(effectiveParentId);
            delegate.started(forwardedDescriptor, startEvent);
            op = new ForwardedOp(forwardedDescriptor);
        } else {
            // Ignore this operation, and map any reference to it to its parent (or whatever its parent is mapped to
            op = new MappedOp(mappedParentId);
        }
        mapping.put(buildOperation.getId(), op);
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        mapping.get(buildOperationId).delegateProgress(progressEvent, delegate);
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        mapping.remove(buildOperation.getId()).delegateFinished(result, delegate);
    }

    public interface Filter {
        boolean shouldForward(BuildOperationDescriptor buildOperation);
    }

    private interface Op {
        @Nullable
        OperationIdentifier getMappedId();
        void delegateProgress(OperationProgressEvent progressEvent, BuildOperationListener delegate);
        void delegateFinished(OperationFinishEvent result, BuildOperationListener delegate);
    }

    private static class MappedOp implements Op {
        private final OperationIdentifier mappedId;

        public MappedOp(@Nullable OperationIdentifier mappedId) {
            this.mappedId = mappedId;
        }

        @Override
        public OperationIdentifier getMappedId() {
            return mappedId;
        }

        @Override
        public void delegateProgress(OperationProgressEvent progressEvent, BuildOperationListener delegate) {
        }

        @Override
        public void delegateFinished(OperationFinishEvent result, BuildOperationListener delegate) {
        }
    }

    private static class ForwardedOp implements Op {
        private final BuildOperationDescriptor forwardedDescriptor;

        public ForwardedOp(BuildOperationDescriptor forwardedDescriptor) {
            this.forwardedDescriptor = forwardedDescriptor;
        }

        @Override
        public OperationIdentifier getMappedId() {
            return forwardedDescriptor.getId();
        }

        @Override
        public void delegateProgress(OperationProgressEvent progressEvent, BuildOperationListener delegate) {
            delegate.progress(forwardedDescriptor.getId(), progressEvent);
        }

        @Override
        public void delegateFinished(OperationFinishEvent result, BuildOperationListener delegate) {
            delegate.finished(forwardedDescriptor, result);
        }
    }
}
