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

import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataBuilder;
import org.gradle.api.artifacts.ComponentMetadataSupplier;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataAdapter;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;

import javax.annotation.Nullable;
import java.util.List;

public class MetadataProvider {
    private final ModuleComponentResolveState resolveState;
    private BuildableModuleComponentMetaDataResolveResult cachedResult;

    public MetadataProvider(ModuleComponentResolveState resolveState) {
        this.resolveState = resolveState;
    }

    public MetadataProvider(BuildableModuleComponentMetaDataResolveResult result) {
        this.resolveState = null;
        cachedResult = result;
    }

    public ComponentMetadata getComponentMetadata() {
        ComponentMetadataSupplier componentMetadataSupplier = resolveState == null ? null : resolveState.getComponentMetadataSupplier();
        if (componentMetadataSupplier != null) {
            final SimpleComponentMetadataBuilder builder = new SimpleComponentMetadataBuilder(DefaultModuleVersionIdentifier.newId(resolveState.getId()));
            ComponentMetadataSupplierDetails details = new ComponentMetadataSupplierDetails() {
                @Override
                public ModuleComponentIdentifier getId() {
                    return resolveState.getId();
                }

                @Override
                public ComponentMetadataBuilder getResult() {
                    return builder;
                }

            };
            componentMetadataSupplier.execute(details);
            if (builder.mutated) {
                return builder.build();
            }
        }
        if (resolve()) {
            return new ComponentMetadataAdapter(getMetaData());
        }
        return null;
    }

    @Nullable
    public IvyModuleDescriptor getIvyModuleDescriptor() {
        ModuleComponentResolveMetadata metaData = getMetaData();
        if (metaData instanceof IvyModuleResolveMetadata) {
            IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metaData;
            return new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus());
        }
        return null;
    }

    public boolean resolve() {
        if (cachedResult == null) {
            cachedResult = resolveState.resolve();
        }
        return cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    public ModuleComponentResolveMetadata getMetaData() {
        resolve();
        return cachedResult.getMetaData();
    }

    public boolean isUsable() {
        return cachedResult == null || cachedResult.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved;
    }

    @Nullable
    public BuildableModuleComponentMetaDataResolveResult getResult() {
        return cachedResult;
    }

    private static class SimpleComponentMetadataBuilder implements ComponentMetadataBuilder {
        private final ModuleVersionIdentifier id;
        private boolean mutated; // used internally to determine if a rule effectively did something

        private String status;
        private List<String> statusScheme = ComponentResolveMetadata.DEFAULT_STATUS_SCHEME;

        private SimpleComponentMetadataBuilder(ModuleVersionIdentifier id) {
            this.id = id;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
            mutated = true;
        }

        @Override
        public void setStatusScheme(List<String> scheme) {
            this.statusScheme = scheme;
            mutated = true;
        }

        ComponentMetadata build() {
            return new UserProvidedMetadata(id, status, statusScheme);
        }

        private static class UserProvidedMetadata implements ComponentMetadata {
            private final ModuleVersionIdentifier id;
            private final String status;
            private final List<String> statusScheme;

            private UserProvidedMetadata(ModuleVersionIdentifier id, String status, List<String> statusScheme) {
                this.id = id;
                this.status = status;
                this.statusScheme = statusScheme;
            }

            @Override
            public ModuleVersionIdentifier getId() {
                return id;
            }

            @Override
            public boolean isChanging() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public List<String> getStatusScheme() {
                return statusScheme;
            }
        }
    }
}
