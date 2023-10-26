/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.RejectedByRuleVersion;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;

class ComponentMetaDataResolveState {
    private final DefaultBuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> resolveResult;
    private final VersionedComponentChooser versionedComponentChooser;
    private final ComponentOverrideMetadata componentOverrideMetadata;
    private final ModuleComponentIdentifier componentIdentifier;

    final ModuleComponentRepository<ModuleComponentGraphResolveState> repository;

    private boolean searchedLocally;
    private boolean searchedRemotely;

    public ComponentMetaDataResolveState(ModuleComponentIdentifier componentIdentifier, ComponentOverrideMetadata componentOverrideMetadata, ModuleComponentRepository<ModuleComponentGraphResolveState> repository, VersionedComponentChooser versionedComponentChooser) {
        this.componentOverrideMetadata = componentOverrideMetadata;
        this.componentIdentifier = componentIdentifier;
        this.repository = repository;
        this.versionedComponentChooser = versionedComponentChooser;
        this.resolveResult = new DefaultBuildableModuleComponentMetaDataResolveResult<>();
    }

    BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> resolve() {
        String oldName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("Resolving " + componentIdentifier.getDisplayName() +  " from " + repository.getName());
            if (!searchedLocally) {
                searchedLocally = true;
                process(repository.getLocalAccess());
                if (resolveResult.hasResult()) {
                    if (resolveResult.isAuthoritative()) {
                        // Don't bother searching remotely
                        searchedRemotely = true;
                    }
                    return resolveResult;
                }
                // If unknown, try a remote search
            }

            if (!searchedRemotely) {
                searchedRemotely = true;
                process(repository.getRemoteAccess());
                return resolveResult;
            }
        } finally {
            Thread.currentThread().setName(oldName);
        }

        throw new IllegalStateException();
    }

    protected void process(ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> moduleAccess) {
        moduleAccess.resolveComponentMetaData(componentIdentifier, componentOverrideMetadata, resolveResult);
        if (resolveResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
            RejectedByRuleVersion rejectedComponent = versionedComponentChooser.isRejectedComponent(componentIdentifier, new CachedMetadataProvider(resolveResult));
            if (rejectedComponent != null) {
                resolveResult.missing();
            }
        }
    }

    protected void applyTo(ResourceAwareResolveResult result) {
        resolveResult.applyTo(result);
    }

    public boolean canMakeFurtherAttempts() {
        return !searchedRemotely;
    }
}
