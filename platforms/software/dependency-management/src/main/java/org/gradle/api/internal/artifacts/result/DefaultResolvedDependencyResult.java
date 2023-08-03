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

import javax.annotation.Nullable;

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

    @Nullable
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
}
