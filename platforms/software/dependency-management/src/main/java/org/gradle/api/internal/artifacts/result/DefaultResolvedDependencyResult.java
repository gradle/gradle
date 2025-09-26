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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Default implementation of {@link ResolvedDependencyResult}.
 */
public class DefaultResolvedDependencyResult implements ResolvedDependencyResult {

    private final ComponentSelector requested;
    private final ResolvedComponentResult from;
    private final boolean constraint;
    private final ResolvedComponentResult selectedComponent;

    // TODO #19788: This should never be null. A resolved dependency result by definition has a target variant.
    // Some bugs in dependency resolution may cause this to be null. We should fix them and make this non-nullable.
    private final @Nullable ResolvedVariantResult selectedVariant;

    private final int hashCode;

    public DefaultResolvedDependencyResult(
        ComponentSelector requested,
        ResolvedComponentResult from,
        boolean constraint,
        ResolvedComponentResult selectedComponent,
        @Nullable ResolvedVariantResult selectedVariant
    ) {
        this.requested = requested;
        this.from = from;
        this.constraint = constraint;
        this.selectedComponent = selectedComponent;
        this.selectedVariant = selectedVariant;

        this.hashCode = computeHashCode(constraint, from, requested, selectedComponent, selectedVariant);
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public ResolvedComponentResult getFrom() {
        return from;
    }

    @Override
    public boolean isConstraint() {
        return constraint;
    }

    @Override
    public ResolvedComponentResult getSelected() {
        return selectedComponent;
    }

    @Override
    public ResolvedVariantResult getResolvedVariant() {
        return selectedVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultResolvedDependencyResult that = (DefaultResolvedDependencyResult) o;
        return constraint == that.constraint &&
            requested.equals(that.requested) &&
            from.equals(that.from) &&
            selectedComponent.equals(that.selectedComponent) &&
            Objects.equals(selectedVariant, that.selectedVariant);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int computeHashCode(
        boolean constraint,
        ResolvedComponentResult from,
        ComponentSelector requested,
        ResolvedComponentResult selectedComponent,
        @Nullable ResolvedVariantResult selectedVariant
    ) {
        int result = requested.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + Boolean.hashCode(constraint);
        result = 31 * result + selectedComponent.hashCode();
        result = 31 * result + Objects.hashCode(selectedVariant);
        return result;
    }

    @Override
    public String toString() {
        if (getRequested().matchesStrictly(getSelected().getId())) {
            return getRequested().toString();
        } else {
            return getRequested() + " -> " + getSelected().getId();
        }
    }

}
