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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier;

import java.util.Collections;
import java.util.Set;

public class RenderableUnresolvedDependencyResult extends AbstractRenderableDependencyResult {
    private final ModuleComponentIdentifier actual;
    private final UnresolvedDependencyResult dependency;

    public RenderableUnresolvedDependencyResult(UnresolvedDependencyResult dependency) {
        this.dependency = dependency;
        ModuleComponentSelector attempted = (ModuleComponentSelector)dependency.getAttempted();
        this.actual = DefaultModuleComponentIdentifier.newId(attempted.getGroup(), attempted.getModule(), attempted.getVersion());
    }

    @Override
    public boolean isResolvable() {
        return false;
    }

    @Override
    protected ModuleComponentSelector getRequested() {
        return (ModuleComponentSelector)dependency.getRequested();
    }

    @Override
    protected ModuleComponentIdentifier getActual() {
        return actual;
    }

    public Set<RenderableDependency> getChildren() {
        return Collections.emptySet();
    }
}
