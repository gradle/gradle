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
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

/**
 * The deserialized representation of an edge in the resolution result.
 * This is produced by first serializing an `EdgeState` and later deserializing when required.
 */
public class DetachedResolvedGraphDependency implements ResolvedGraphDependency {

    private final ComponentSelector requested;
    private final @Nullable ModuleVersionResolveException failure;
    private final @Nullable ComponentSelectionReason reason;
    private final @Nullable Long selectedComponent;
    private final @Nullable Long selectedVariant;
    private final boolean constraint;

    public static DetachedResolvedGraphDependency failure(
        ComponentSelector requested,
        ModuleVersionResolveException failure,
        ComponentSelectionReason reason,
        boolean constraint
    ) {
        return new DetachedResolvedGraphDependency(requested, reason, failure, null, null, constraint);
    }

    public static DetachedResolvedGraphDependency success(
        ComponentSelector requested,
        long selectedComponent,
        long selectedVariant,
        boolean constraint
    ) {
        return new DetachedResolvedGraphDependency(requested, null, null, selectedComponent, selectedVariant, constraint);
    }

    private DetachedResolvedGraphDependency(
        ComponentSelector requested,
        @Nullable ComponentSelectionReason reason,
        @Nullable ModuleVersionResolveException failure,
        @Nullable Long selectedComponent,
        @Nullable Long selectedVariant,
        boolean constraint
    ) {
        if (failure != null) {
            assert reason != null;
            assert selectedComponent == null && selectedVariant == null;
        } else {
            assert reason == null;
            assert selectedComponent != null && selectedVariant != null;
        }

        this.requested = requested;
        this.reason = reason;
        this.selectedComponent = selectedComponent;
        this.failure = failure;
        this.constraint = constraint;
        this.selectedVariant = selectedVariant;
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public @Nullable ModuleVersionResolveException getFailure() {
        return failure;
    }

    @Override
    public ComponentSelectionReason getReason() {
        if (reason == null) {
            throw new IllegalStateException("Edge does not have a failure reason.");
        }
        return reason;
    }

    @Override
    public long getSelectedComponentId() {
        if (selectedComponent == null) {
            throw new IllegalStateException("Edge does not have a selected component.");
        }
        return selectedComponent;
    }

    @Override
    public long getSelectedVariantId() {
        if (selectedVariant == null) {
            throw new IllegalStateException("Edge does not have a selected variant.");
        }
        return selectedVariant;
    }

    @Override
    public boolean isConstraint() {
        return constraint;
    }

}
