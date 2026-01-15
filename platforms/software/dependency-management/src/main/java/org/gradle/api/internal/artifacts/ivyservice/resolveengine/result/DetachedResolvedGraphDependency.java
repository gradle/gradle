/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

/**
 * The deserialized representation of an edge in the resolution result.
 * This is produced by first serializing an `EdgeState` and later deserializing when required.
 */
public class DetachedResolvedGraphDependency implements ResolvedGraphDependency {

    private final ComponentSelector requested;
    private final @Nullable Long targetComponent;
    private final @Nullable ComponentSelectionReasonInternal reason;
    private final @Nullable ModuleVersionResolveException failure;
    private final boolean constraint;
    private final @Nullable Long targetVariant;

    public DetachedResolvedGraphDependency(
        ComponentSelector requested,
        @Nullable Long targetComponent,
        @Nullable ComponentSelectionReasonInternal reason,
        @Nullable ModuleVersionResolveException failure,
        boolean constraint,
        @Nullable Long targetVariant
    ) {
        assert (failure != null && reason != null) || (targetComponent != null && targetVariant != null);

        this.requested = requested;
        this.reason = reason;
        this.targetComponent = targetComponent;
        this.failure = failure;
        this.constraint = constraint;
        this.targetVariant = targetVariant;
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public @Nullable Long getTargetComponentId() {
        return targetComponent;
    }

    @Override
    public @Nullable ComponentSelectionReasonInternal getReason() {
        return reason;
    }

    @Override
    public @Nullable ModuleVersionResolveException getFailure() {
        return failure;
    }

    @Override
    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public @Nullable Long getTargetVariantId() {
        return targetVariant;
    }

}
