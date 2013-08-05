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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableModuleVersionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultDependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.AbstractDescriptorParseContext;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class ExternalResourceResolverDescriptorParseContext extends AbstractDescriptorParseContext {
    private final DependencyToModuleVersionResolver mainResolver;
    private final ExternalResourceResolver moduleResolver;
    private final ModuleRevisionId moduleRevisionId;

    public ExternalResourceResolverDescriptorParseContext(DependencyToModuleVersionResolver mainResolver, ExternalResourceResolver moduleResolver, ModuleRevisionId moduleRevisionId) {
        super("integration");
        this.mainResolver = mainResolver;
        this.moduleResolver = moduleResolver;
        this.moduleRevisionId = moduleRevisionId;
    }

    public ModuleRevisionId getCurrentRevisionId() {
        return moduleRevisionId;
    }

    public ModuleDescriptor getModuleDescriptor(ModuleRevisionId moduleRevisionId) {
        DefaultBuildableModuleVersionResolveResult result = new DefaultBuildableModuleVersionResolveResult();
        mainResolver.resolve(new DefaultDependencyMetaData(new DefaultDependencyDescriptor(moduleRevisionId, true)), result);
        return result.getMetaData().getDescriptor();
    }

    public boolean artifactExists(Artifact artifact) {
        return !ArtifactOrigin.isUnknown(moduleResolver.locate(artifact));
    }
}
