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

public class DefaultResolvedDependencyResult implements ResolvedDependencyResult {

    private final ComponentSelector requested;
    private final ResolvedComponentResult from;
    private final boolean constraint;
    private final ResolvedComponentResult selectedComponent;
    private final ResolvedVariantResult selectedVariant;

    private final int hashCode;

    public DefaultResolvedDependencyResult(
        ComponentSelector requested,
        boolean constraint,
        ResolvedComponentResult from,
        ResolvedComponentResult selectedComponent,
        ResolvedVariantResult selectedVariant
    ) {
        this.requested = requested;
        this.from = from;
        this.constraint = constraint;
        this.selectedComponent = selectedComponent;
        this.selectedVariant = selectedVariant;

        this.hashCode = computeHashCode(requested, from, constraint, selectedComponent, selectedVariant);
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
    public String toString() {
        if (getRequested().matchesStrictly(getSelected().getId())) {
            return getRequested().toString();
        } else {
            return getRequested() + " -> " + getSelected().getId();
        }
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultResolvedDependencyResult that = (DefaultResolvedDependencyResult) o;
        return requested.equals(that.requested) &&
            from.equals(that.from) &&
            constraint == that.constraint &&
            selectedComponent.equals(that.selectedComponent) &&
            selectedVariant.equals(that.selectedVariant);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private static int computeHashCode(
        ComponentSelector requested,
        ResolvedComponentResult from,
        boolean constraint,
        ResolvedComponentResult selectedComponent,
        ResolvedVariantResult selectedVariant
    ) {
        int result = requested.hashCode();
        result = 31 * result + from.hashCode();
        result = 31 * result + Boolean.hashCode(constraint);
        result = 31 * result + selectedComponent.hashCode();
        result = 31 * result + selectedVariant.hashCode();
        return result;
    }

}
