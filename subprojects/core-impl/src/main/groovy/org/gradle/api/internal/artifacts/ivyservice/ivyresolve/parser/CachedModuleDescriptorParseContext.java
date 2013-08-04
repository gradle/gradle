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
 * Context used for parsing cached module descriptor files.
 * Will only be used for parsing ivy.xml files, as pom files are converted before caching.
 */
public class CachedModuleDescriptorParseContext extends AbstractDescriptorParseContext {
    private final DependencyResolver mainResolver;

    public CachedModuleDescriptorParseContext(DependencyResolver mainResolver, String defaultStatus) {
        super(defaultStatus);
        this.mainResolver = mainResolver;
    }

    public ModuleRevisionId getCurrentRevisionId() {
        throw new UnsupportedOperationException();
    }

    public boolean artifactExists(Artifact artifact) {
        throw new UnsupportedOperationException();
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

        ResolvedModuleRevision otherModule = mainResolver.getDependency(dd, data);
        if (otherModule == null) {
            throw new ParseException("Unable to find " + moduleRevisionId.toString(), 0);
        }
        return otherModule.getDescriptor();
    }
}
