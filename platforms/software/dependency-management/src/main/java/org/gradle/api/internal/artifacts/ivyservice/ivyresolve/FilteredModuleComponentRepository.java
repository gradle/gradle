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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.repositories.ArtifactResolutionDetails;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Factory;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

public class FilteredModuleComponentRepository implements ModuleComponentRepository<ModuleComponentGraphResolveState> {
    private final ModuleComponentRepository<ModuleComponentGraphResolveState> delegate;
    private final Action<? super ArtifactResolutionDetails> filterAction;

    public FilteredModuleComponentRepository(ModuleComponentRepository<ModuleComponentGraphResolveState> delegate, Action<? super ArtifactResolutionDetails> filterAction) {
        this.delegate = delegate;
        this.filterAction = filterAction;
    }

    public Action<? super ArtifactResolutionDetails> getFilterAction() {
        return filterAction;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> getLocalAccess() {
        return new FilteringAccess(delegate.getLocalAccess());
    }

    @Override
    public ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> getRemoteAccess() {
        return new FilteringAccess(delegate.getRemoteAccess());
    }

    @Override
    public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
        return delegate.getArtifactCache();
    }

    @Nullable
    @Override
    public InstantiatingAction<ComponentMetadataSupplierDetails> getComponentMetadataSupplier() {
        return delegate.getComponentMetadataSupplier();
    }

    private class FilteringAccess implements ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> {
        private final ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> delegate;

        private FilteringAccess(ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            ModuleIdentifier identifier = dependency.getSelector().getModuleIdentifier();
            whenModulePresent(identifier, null,
                    () -> delegate.listModuleVersions(dependency, result),
                    () -> result.listed(Collections.emptyList()));
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            whenModulePresent(moduleComponentIdentifier.getModuleIdentifier(), moduleComponentIdentifier,
                    () -> delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result),
                result::missing);
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            delegate.resolveArtifactsWithType(component, artifactType, result);
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            delegate.resolveArtifact(artifact, moduleSources, result);
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return whenModulePresent(moduleComponentIdentifier.getModuleIdentifier(), moduleComponentIdentifier,
                    () -> delegate.estimateMetadataFetchingCost(moduleComponentIdentifier),
                    () -> MetadataFetchingCost.FAST);
        }

        private void whenModulePresent(ModuleIdentifier id, @Nullable ModuleComponentIdentifier moduleComponentIdentifier, Runnable present, Runnable absent) {
            DefaultArtifactResolutionDetails details = new DefaultArtifactResolutionDetails(id, moduleComponentIdentifier);
            filterAction.execute(details);
            if (details.notFound) {
                absent.run();
            } else {
                present.run();
            }
        }

        private <T> T whenModulePresent(ModuleIdentifier id, ModuleComponentIdentifier moduleComponentIdentifier, Factory<T> present, Factory<T> absent) {
            DefaultArtifactResolutionDetails details = new DefaultArtifactResolutionDetails(id, moduleComponentIdentifier);
            filterAction.execute(details);
            if (details.notFound) {
                return absent.create();
            }
            return present.create();
        }
    }

    private static class DefaultArtifactResolutionDetails implements ArtifactResolutionDetails {
        private final ModuleIdentifier moduleIdentifier;
        private final ModuleComponentIdentifier moduleComponentIdentifier;
        private boolean notFound;

        private DefaultArtifactResolutionDetails(ModuleIdentifier moduleIdentifier, @Nullable ModuleComponentIdentifier componentId) {
            this.moduleIdentifier = moduleIdentifier;
            this.moduleComponentIdentifier = componentId;
        }

        @Override
        public ModuleIdentifier getModuleId() {
            return moduleIdentifier;
        }

        @Override
        @Nullable
        public ModuleComponentIdentifier getComponentId() {
            return moduleComponentIdentifier;
        }

        @Override
        public boolean isVersionListing() {
            return moduleComponentIdentifier == null;
        }

        @Override
        public void notFound() {
            notFound = true;
        }
    }
}
