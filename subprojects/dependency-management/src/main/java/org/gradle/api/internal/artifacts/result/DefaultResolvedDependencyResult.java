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

public class DefaultResolvedDependencyResult extends AbstractDependencyResult implements ResolvedDependencyResult {
    private final ResolvedComponentResult selectedComponent;
    private final ResolvedVariantResult selectedVariant;

    public DefaultResolvedDependencyResult(ComponentSelector requested,
                                           boolean constraint,
                                           ResolvedComponentResult selectedComponent,
                                           ResolvedVariantResult selectedVariant,
                                           ResolvedComponentResult from) {
        super(requested, from, constraint);
        this.selectedComponent = selectedComponent;
        this.selectedVariant = selectedVariant;
    }

    @Override
    public ResolvedComponentResult getSelected() {
        return selectedComponent;
    }

    public ResolvedVariantResult getResolvedVariant() {
        return selectedVariant;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        DefaultResolvedDependencyResult that = (DefaultResolvedDependencyResult) o;

        if (!selectedComponent.equals(that.selectedComponent)) {
            return false;
        }
        return selectedVariant != null ? selectedVariant.equals(that.selectedVariant) : that.selectedVariant == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + selectedComponent.hashCode();
        result = 31 * result + (selectedVariant != null ? selectedVariant.hashCode() : 0);
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
