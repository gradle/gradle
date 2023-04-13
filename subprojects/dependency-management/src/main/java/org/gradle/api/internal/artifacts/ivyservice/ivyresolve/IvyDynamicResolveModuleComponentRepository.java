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
import org.gradle.internal.component.external.model.DefaultModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

/**
 * A ModuleComponentRepository that provides the 'dynamic resolve mode' for an Ivy repository, where the 'revConstraint'
 * attribute is used for versions instead of the 'rev' attribute.
 */
class IvyDynamicResolveModuleComponentRepository extends BaseModuleComponentRepository<ModuleComponentGraphResolveState> {

    IvyDynamicResolveModuleComponentRepository(ModuleComponentRepository<ModuleComponentGraphResolveState> delegate) {
        super(delegate,
            new IvyDynamicResolveModuleComponentRepositoryAccess(delegate.getLocalAccess()),
            new IvyDynamicResolveModuleComponentRepositoryAccess(delegate.getRemoteAccess()));
    }

    private static class IvyDynamicResolveModuleComponentRepositoryAccess extends BaseModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> {

        IvyDynamicResolveModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess<ModuleComponentGraphResolveState> delegate) {
            super(delegate);
        }

        @Override
        public String toString() {
            return "Ivy dynamic resolve > " + getDelegate();
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            super.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
            if (result.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
                transformDependencies(result);
            }
        }

        private void transformDependencies(BuildableModuleComponentMetaDataResolveResult<ModuleComponentGraphResolveState> result) {
            ModuleComponentResolveMetadata metadata = result.getMetaData().getModuleResolveMetadata();
            if (metadata instanceof IvyModuleResolveMetadata) {
                IvyModuleResolveMetadata transformedMetadata = ((IvyModuleResolveMetadata) metadata).withDynamicConstraintVersions();
                result.setMetadata(new DefaultModuleComponentGraphResolveState(transformedMetadata));
            }
        }
    }
}
