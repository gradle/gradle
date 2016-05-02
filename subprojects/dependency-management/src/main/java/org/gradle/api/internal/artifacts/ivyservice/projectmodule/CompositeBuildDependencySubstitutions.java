/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRuleProvider;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;

public class CompositeBuildDependencySubstitutions implements DependencySubstitutionRuleProvider {
    private final CompositeProjectComponentRegistry projectComponentRegistry;

    public CompositeBuildDependencySubstitutions(CompositeProjectComponentRegistry projectComponentRegistry) {
        this.projectComponentRegistry = projectComponentRegistry;
    }

    @Override
    public Action<DependencySubstitution> getDependencySubstitutionRule() {
        return new Action<DependencySubstitution>() {
            @Override
            public void execute(DependencySubstitution sub) {
                DependencySubstitutionInternal dependencySubstitution = (DependencySubstitutionInternal) sub;
                // Use the result of previous rules as the input for dependency substitution
                ComponentSelector requested = dependencySubstitution.getTarget();
                if (requested instanceof ModuleComponentSelector) {
                    ModuleComponentSelector selector = (ModuleComponentSelector) requested;
                    ProjectComponentIdentifier replacement = projectComponentRegistry.getReplacementProject(selector);
                    if (replacement != null) {
                        dependencySubstitution.useTarget(
                            DefaultProjectComponentSelector.newSelector(replacement.getProjectPath()),
                            VersionSelectionReasons.COMPOSITE_BUILD);
                    }
                }
            }
        };
    }
}
