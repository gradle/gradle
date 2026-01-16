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
 * The deserialized representation of a successful edge in the resolution result.
 */
public class DetachedSuccessfulResolvedGraphDependency implements ResolvedGraphDependency {

    private final boolean constraint;
    private final ComponentSelector requested;
    private final long targetComponent;
    private final long targetVariant;

    public DetachedSuccessfulResolvedGraphDependency(
        boolean constraint,
        ComponentSelector requested,
        long targetComponent,
        long targetVariant
    ) {
        this.constraint = constraint;
        this.requested = requested;
        this.targetComponent = targetComponent;
        this.targetVariant = targetVariant;
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
    public long getTargetComponentId() {
        return targetComponent;
    }

    @Override
    public long getTargetVariantId() {
        return targetVariant;
    }

    @Override
    public @Nullable ComponentSelectionReasonInternal getReason() {
        return null;
    }

    @Override
    public @Nullable ModuleVersionResolveException getFailure() {
        return null;
    }

}
