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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.settings.IvySettings;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;

/**
 * A {@link ModuleVersionRepository} wrapper around an {@link ExternalResourceResolver}.
 */
public class ExternalResourceResolverAdapter implements IvyAwareModuleVersionRepository {
    private final ExternalResourceResolver resolver;
    private final boolean dynamicResolve;

    public ExternalResourceResolverAdapter(ExternalResourceResolver resolver, boolean dynamicResolve) {
        this.resolver = resolver;
        this.dynamicResolve = dynamicResolve;
    }

    public String getId() {
        return resolver.getId();
    }

    public String getName() {
        return resolver.getName();
    }

    @Override
    public String toString() {
        return resolver.toString();
    }

    public boolean isLocal() {
        return resolver.isLocal();
    }

    public void setSettings(IvySettings settings) {
        resolver.setSettings(settings);
    }

    public void setResolveData(ResolveData resolveData) {
    }

    public boolean isDynamicResolveMode() {
        return dynamicResolve;
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        resolver.getDependency(dependency.getDescriptor(), result);
    }

    public void resolve(ArtifactIdentifier artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        resolver.resolve(artifact, result, moduleSource);
    }
}
