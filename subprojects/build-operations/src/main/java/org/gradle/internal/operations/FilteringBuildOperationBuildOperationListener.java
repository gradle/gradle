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
    // A map from progress operation id seen in event -> progress operation id that should be forwarded
    private final Map<OperationIdentifier, OperationIdentifier> parentMapping = new ConcurrentHashMap<OperationIdentifier, OperationIdentifier>();
    // A set of progress operations that have been forwarded
    private final Map<OperationIdentifier, BuildOperationDescriptor> forwarded = new ConcurrentHashMap<OperationIdentifier, BuildOperationDescriptor>();

    private final BuildOperationListener delegate;
    private final Filter filter;

    public FilteringBuildOperationBuildOperationListener(BuildOperationListener delegate, Filter filter) {
        this.delegate = delegate;
        this.filter = filter;
    }

    @Override
    public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
        OperationIdentifier id = buildOperation.getId();
        OperationIdentifier parentId = buildOperation.getParentId();
        OperationIdentifier mappedParent = getEffectiveId(parentId);
        if (filter.shouldForward(buildOperation)) {
            BuildOperationDescriptor forwardedDescriptor = buildOperation.withParentId(mappedParent);
            forwarded.put(id, forwardedDescriptor);
            delegate.started(forwardedDescriptor, startEvent);
        } else {
            // Ignore this operation, and map any reference to it to its parent (or whatever its parent is mapped to
            if (mappedParent != null) {
                parentMapping.put(id, mappedParent);
            }
        }
    }

    @Override
    public void progress(OperationIdentifier buildOperationId, OperationProgressEvent progressEvent) {
        if (forwarded.containsKey(buildOperationId)) {
            delegate.progress(buildOperationId, progressEvent);
        }
    }

    @Override
    public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent result) {
        OperationIdentifier id = buildOperation.getId();
        OperationIdentifier mappedEvent = parentMapping.remove(id);
        if (mappedEvent != null) {
            return;
        }
        BuildOperationDescriptor forwardedDescriptor = forwarded.remove(id);
        if (forwardedDescriptor != null) {
            delegate.finished(forwardedDescriptor, result);
        }
    }

    @Nullable
    private OperationIdentifier getEffectiveId(@Nullable OperationIdentifier id) {
        if (id == null) {
            return null;
        }
        OperationIdentifier effectiveId = parentMapping.get(id);
        return effectiveId == null
            ? id
            : effectiveId;
    }

    public interface Filter {
        boolean shouldForward(BuildOperationDescriptor buildOperation);
    }
}
