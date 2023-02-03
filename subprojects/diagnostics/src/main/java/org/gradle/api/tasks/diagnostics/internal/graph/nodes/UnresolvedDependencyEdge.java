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

package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class UnresolvedDependencyEdge implements DependencyEdge {
    private final UnresolvedDependencyResult dependency;
    private final ComponentIdentifier actual;

    public UnresolvedDependencyEdge(UnresolvedDependencyResult dependency) {
        this.dependency = dependency;

        if (dependency.getAttempted() instanceof ModuleComponentSelector) {
            ModuleComponentSelector attempted = (ModuleComponentSelector) dependency.getAttempted();
            actual = DefaultModuleComponentIdentifier.newId(attempted.getModuleIdentifier(), attempted.getVersion());
        } else if (dependency.getAttempted() instanceof DefaultProjectComponentSelector) {
            DefaultProjectComponentSelector attempted = (DefaultProjectComponentSelector) dependency.getAttempted();
            actual = new DefaultProjectComponentIdentifier(attempted.getBuildIdentifier(), attempted.getIdentityPath(), attempted.projectPath(), attempted.getProjectName());
        } else {
            actual = () -> dependency.getAttempted().getDisplayName();
        }
    }

    public Throwable getFailure() {
        return dependency.getFailure();
    }

    @Override
    public boolean isResolvable() {
        return false;
    }

    @Override
    public ComponentSelector getRequested() {
        return dependency.getRequested();
    }

    @Override
    public ComponentIdentifier getActual() {
        return actual;
    }

    @Override
    public ComponentSelectionReason getReason() {
        return dependency.getAttemptedReason();
    }

    @Override
    public List<ResolvedVariantResult> getSelectedVariants() {
        return Collections.emptyList();
    }

    @Override
    public List<ResolvedVariantResult> getAllVariants() {
        return Collections.emptyList();
    }

    @Override
    public ComponentIdentifier getFrom() {
        return dependency.getFrom().getId();
    }

    @Override
    public Set<? extends RenderableDependency> getChildren() {
        return Collections.singleton(new InvertedRenderableModuleResult(dependency.getFrom()));
    }
}
