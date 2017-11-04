/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.internal.dependencylock.model.DependencyLock;
import org.gradle.internal.dependencylock.model.ModuleKey;

public class DefaultDependencyLockState implements DependencyLockState {

    private final DependencyLock dependencyLock = new DependencyLock();
    private boolean successfulDependencyResolution = true;

    @Override
    public void populateFrom(final String projectPath, final String configurationName, ResolutionResult resolutionResult) {
        resolutionResult.allDependencies(new Action<DependencyResult>() {
            @Override
            public void execute(DependencyResult dependencyResult) {
                if (dependencyResult instanceof ResolvedDependencyResult) {
                    ResolvedDependencyResult resolvedDependencyResult = (ResolvedDependencyResult) dependencyResult;
                    ComponentSelector requested = dependencyResult.getRequested();

                    if (requested instanceof ModuleComponentSelector) {
                        ModuleComponentSelector requestedModule = (ModuleComponentSelector) requested;
                        addDependency(projectPath, configurationName, requestedModule, resolvedDependencyResult, dependencyLock);
                    }
                } else if (encounteredFirstUnresolvedDependency(dependencyResult)) {
                    successfulDependencyResolution = false;
                }
            }
        });
    }

    private void addDependency(String projectPath, String configurationName, ModuleComponentSelector requestedModule, ResolvedDependencyResult resolvedDependencyResult, DependencyLock dependencyLock) {
        ModuleIdentifier moduleIdentifier = new ModuleKey(requestedModule.getGroup(), requestedModule.getModule());
        String resolvedVersion = determineResolvedVersion(requestedModule, resolvedDependencyResult);
        dependencyLock.addDependency(projectPath, configurationName, moduleIdentifier, resolvedVersion);
    }

    private String determineResolvedVersion(ModuleComponentSelector requestedModule, ResolvedDependencyResult resolvedDependencyResult) {
        boolean selectedByRule = resolvedDependencyResult.getSelected().getSelectionReason().isSelectedByRule();
        return selectedByRule ? requestedModule.getVersion() : resolvedDependencyResult.getSelected().getModuleVersion().getVersion();
    }

    private boolean encounteredFirstUnresolvedDependency(DependencyResult dependencyResult) {
        return successfulDependencyResolution && dependencyResult instanceof UnresolvedDependencyResult;
    }

    @Override
    public boolean isSuccessfulDependencyResolution() {
        return successfulDependencyResolution;
    }

    @Override
    public DependencyLock getDependencyLock() {
        return dependencyLock;
    }
}
