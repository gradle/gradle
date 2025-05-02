/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ResolvedVariantResult;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class AbstractRenderableDependency implements RenderableDependency {

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public List<ResolvedVariantResult> getResolvedVariants() {
        return Collections.emptyList();
    }

    @Override
    public List<ResolvedVariantResult> getAllVariants() {
        return Collections.emptyList();
    }

    @Override
    public ResolutionState getResolutionState() {
        return ResolutionState.UNRESOLVED;
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return Collections.emptySet();
    }

    @Override
    public List<Section> getExtraDetails() {
        return Collections.emptyList();
    }

    protected boolean exactMatch(ComponentSelector requested, ComponentIdentifier selected) {
        if (requested instanceof ModuleComponentSelector) {
            VersionConstraint versionConstraint = ((ModuleComponentSelector) requested).getVersionConstraint();
            if (!(versionConstraint.getRequiredVersion().isEmpty()
                    || versionConstraint.getDisplayName().equals(versionConstraint.getRequiredVersion()))) {
                return false;
            }
        }
        return requested.matchesStrictly(selected);
    }
}
