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

/**
 * The deserialized representation of a failed edge in the resolution result.
 */
public class DetachedFailedResolvedGraphDependency implements ResolvedGraphDependency {

    private final boolean constraint;
    private final ComponentSelector requested;
    private final ModuleVersionResolveException failure;
    private final ComponentSelectionReasonInternal reason;

    public DetachedFailedResolvedGraphDependency(
        boolean constraint,
        ComponentSelector requested,
        ModuleVersionResolveException failure,
        ComponentSelectionReasonInternal reason
    ) {
        this.constraint = constraint;
        this.requested = requested;
        this.failure = failure;
        this.reason = reason;
    }

    @Override
    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        return failure;
    }

    @Override
    public ComponentSelectionReasonInternal getReason() {
        return reason;
    }

    @Override
    public long getTargetComponentId() {
        throw new IllegalStateException("Cannot get target component ID for failed dependency");
    }

    @Override
    public long getTargetVariantId() {
        throw new IllegalStateException("Cannot get target variant ID for failed dependency");
    }

}
