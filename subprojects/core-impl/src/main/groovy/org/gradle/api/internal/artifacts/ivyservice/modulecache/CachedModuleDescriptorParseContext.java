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

package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.DefaultBuildableModuleVersionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultDependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.AbstractDescriptorParseContext;

import java.text.ParseException;

/**
 * Context used for parsing cached module descriptor files.
 * Will only be used for parsing ivy.xml files, as pom files are converted before caching.
 */
class CachedModuleDescriptorParseContext extends AbstractDescriptorParseContext {
    private final DependencyToModuleVersionResolver resolver;

    public CachedModuleDescriptorParseContext(DependencyToModuleVersionResolver resolver, String defaultStatus) {
        super(defaultStatus);
        this.resolver = resolver;
    }

    public ModuleRevisionId getCurrentRevisionId() {
        throw new UnsupportedOperationException();
    }

    public boolean artifactExists(Artifact artifact) {
        throw new UnsupportedOperationException();
    }

    public ModuleDescriptor getModuleDescriptor(ModuleRevisionId moduleRevisionId) throws ParseException {
        DefaultBuildableModuleVersionResolveResult result = new DefaultBuildableModuleVersionResolveResult();
        resolver.resolve(new DefaultDependencyMetaData(new DefaultDependencyDescriptor(moduleRevisionId, true)), result);

        if (result.getFailure() != null) {
            throw new ParseException("Unable to find " + moduleRevisionId.toString(), 0);
        }
        return result.getMetaData().getDescriptor();
    }
}
