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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser;

import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;

import java.text.ParseException;

/**
 * ParserSettings that control the scope of searches carried out during parsing.
 * If the parser asks for a resolver for the currently resolving revision, the resolver scope is only the repository where the module was resolved.
 * If the parser asks for a resolver for a different revision, the resolver scope is all repositories.
 */
public class ModuleScopedDescriptorParseContext extends AbstractDescriptorParseContext {
    private final DependencyResolver mainResolver;
    private final DependencyResolver moduleResolver;
    private final ModuleRevisionId moduleRevisionId;

    public ModuleScopedDescriptorParseContext(DependencyResolver mainResolver, DependencyResolver moduleResolver, ModuleRevisionId moduleRevisionId, String defaultStatus) {
        super(defaultStatus);
        this.mainResolver = mainResolver;
        this.moduleResolver = moduleResolver;
        this.moduleRevisionId = moduleRevisionId;
    }

    public ModuleRevisionId getCurrentRevisionId() {
        return moduleRevisionId;
    }

    public ModuleDescriptor getModuleDescriptor(ModuleRevisionId moduleRevisionId) throws ParseException {
        DependencyDescriptor dd = new DefaultDependencyDescriptor(moduleRevisionId, true);
        ResolveData data = IvyContext.getContext().getResolveData();
        if (data == null) {
            ResolveEngine engine = IvyContext.getContext().getIvy().getResolveEngine();
            ResolveOptions options = new ResolveOptions();
            options.setDownload(false);
            data = new ResolveData(engine, options);
        }

        DependencyResolver resolver = getResolver(moduleRevisionId);
        if (resolver == null) {
            // TODO: Throw exception here?
            return null;
        } else {
            ResolvedModuleRevision otherModule = resolver.getDependency(dd, data);
            if (otherModule == null) {
                throw new ParseException("Unable to find " + moduleRevisionId.toString(), 0);
            }
            return otherModule.getDescriptor();
        }
    }

    public boolean artifactExists(Artifact artifact) {
        DependencyResolver resolver = getResolver(artifact.getModuleRevisionId());
        ArtifactOrigin artifactOrigin = resolver.locate(artifact);
        return !ArtifactOrigin.isUnknown(artifactOrigin);
    }

    private DependencyResolver getResolver(ModuleRevisionId mRevId) {
        if (mRevId.equals(moduleRevisionId)) {
            return moduleResolver;
        }
        return mainResolver;
    }
}
