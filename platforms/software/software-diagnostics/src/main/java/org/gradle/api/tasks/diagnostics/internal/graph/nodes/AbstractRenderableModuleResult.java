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
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class AbstractRenderableModuleResult extends AbstractRenderableDependency {

    protected final ResolvedComponentResult module;

    public AbstractRenderableModuleResult(ResolvedComponentResult module) {
        this.module = module;
    }

    @Nonnull
    @Override
    public ComponentIdentifier getId() {
        return module.getId();
    }

    @Override
    public String getName() {
        return getId().getDisplayName();
    }

    @Override
    public List<ResolvedVariantResult> getResolvedVariants() {
        return module.getVariants();
    }

    @Override
    public List<ResolvedVariantResult> getAllVariants() {
        return ((ResolvedComponentResultInternal) module).getAvailableVariants();
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public ResolutionState getResolutionState() {
        return ResolutionState.RESOLVED;
    }

    @Override
    public String toString() {
        return module.toString();
    }
}
