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
package org.gradle.api.tasks.diagnostics.internal.html;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.specs.Spec;

/**
 * A DependencyResultSpec that matches requested and selected modules if their
 * group and name match strictly with the given group and name
 * @author JB
 */
public class StrictDependencyResultSpec implements Spec<DependencyResult> {
    private final ModuleIdentifier moduleIdentifier;

    public StrictDependencyResultSpec(ModuleIdentifier moduleIdentifier) {
        this.moduleIdentifier = moduleIdentifier;
    }

    public boolean isSatisfiedBy(DependencyResult candidate) {
        if (candidate instanceof ResolvedDependencyResult) {
            return matchesRequested(candidate) || matchesSelected((ResolvedDependencyResult) candidate);
        } else {
            return matchesRequested(candidate);
        }
    }

    private boolean matchesRequested(DependencyResult candidate) {
        return candidate.getRequested().getGroup().equals(moduleIdentifier.getGroup())
               && candidate.getRequested().getName().equals(moduleIdentifier.getName());
    }

    private boolean matchesSelected(ResolvedDependencyResult candidate) {
        ModuleVersionIdentifier selected = candidate.getSelected().getId();
        return selected.getGroup().equals(moduleIdentifier.getGroup())
               && selected.getName().equals(moduleIdentifier.getName());
    }
}
