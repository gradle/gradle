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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ExternalComponentResolveMetadata;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

/**
 * A ModuleComponentRepository that provides the 'dynamic resolve mode' for an Ivy repository, where the 'revConstraint'
 * attribute is used for versions instead of the 'rev' attribute.
 */
class IvyDynamicResolveModuleComponentRepository extends BaseModuleComponentRepository<ExternalModuleComponentGraphResolveState> {
    IvyDynamicResolveModuleComponentRepository(ModuleComponentRepository<ExternalModuleComponentGraphResolveState> delegate, ModuleComponentGraphResolveStateFactory resolveStateFactory) {
        super(delegate,
            new IvyDynamicResolveModuleComponentRepositoryAccess(delegate.getLocalAccess(), resolveStateFactory),
            new IvyDynamicResolveModuleComponentRepositoryAccess(delegate.getRemoteAccess(), resolveStateFactory));
    }

    private static class IvyDynamicResolveModuleComponentRepositoryAccess extends BaseModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState> {
        private final ModuleComponentGraphResolveStateFactory resolveStateFactory;

        IvyDynamicResolveModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess<ExternalModuleComponentGraphResolveState> delegate, ModuleComponentGraphResolveStateFactory resolveStateFactory) {
            super(delegate);
            this.resolveStateFactory = resolveStateFactory;
        }

        @Override
        public String toString() {
            return "Ivy dynamic resolve > " + getDelegate();
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState> result) {
            super.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
            if (result.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
                transformDependencies(result);
            }
        }

        private void transformDependencies(BuildableModuleComponentMetaDataResolveResult<ExternalModuleComponentGraphResolveState> result) {
            @SuppressWarnings("deprecation")
            ExternalComponentResolveMetadata legacyMetadata = result.getMetaData().getLegacyMetadata();
            if (legacyMetadata instanceof IvyModuleResolveMetadata) {
                IvyModuleResolveMetadata transformedMetadata = ((IvyModuleResolveMetadata) legacyMetadata).withDynamicConstraintVersions();
                result.setMetadata(resolveStateFactory.stateFor(transformedMetadata));
            }
        }
    }
}
