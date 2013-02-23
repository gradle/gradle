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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultBuildableModuleVersionDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionDescriptor;

public class DefaultBuildableModuleVersionResolveResult implements BuildableModuleVersionResolveResult {
    private ModuleVersionIdentifier moduleVersionIdentifier;
    private ModuleDescriptor moduleDescriptor;
    private ModuleVersionResolveException failure;
    private ArtifactResolver artifactResolver;

    public DefaultBuildableModuleVersionResolveResult failed(ModuleVersionResolveException failure) {
        moduleDescriptor = null;
        this.failure = failure;
        return this;
    }

    public void notFound(ModuleVersionIdentifier moduleVersionIdentifier) {
        failed(new ModuleVersionNotFoundException(moduleVersionIdentifier));
    }

    public void resolved(ModuleVersionIdentifier moduleVersionId, ModuleDescriptor descriptor, ArtifactResolver artifactResolver) {
        this.moduleVersionIdentifier = moduleVersionId;
        this.moduleDescriptor = descriptor;
        this.artifactResolver = artifactResolver;
    }

    public void setMetaData(ModuleRevisionId moduleRevisionId, ModuleDescriptor descriptor) {
        assertResolved();
        this.moduleVersionIdentifier = toModuleVersionIdentifier(moduleRevisionId);
        this.moduleDescriptor = descriptor;
    }

    public void setArtifactResolver(ArtifactResolver artifactResolver) {
        assertResolved();
        this.artifactResolver = artifactResolver;
    }

    public ModuleVersionIdentifier getId() throws ModuleVersionResolveException {
        assertResolved();
        return moduleVersionIdentifier;
    }

    public ModuleDescriptor getDescriptor() throws ModuleVersionResolveException {
        assertResolved();
        return moduleDescriptor;
    }

    public ModuleVersionDescriptor getMetaData() throws ModuleVersionResolveException {
        assertResolved();
        DefaultBuildableModuleVersionDescriptor metaData = new DefaultBuildableModuleVersionDescriptor();
        metaData.resolved(moduleVersionIdentifier, moduleDescriptor, false, null);
        return metaData;
    }

    public ArtifactResolver getArtifactResolver() throws ModuleVersionResolveException {
        assertResolved();
        return artifactResolver;
    }

    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
    }

    private ModuleVersionIdentifier toModuleVersionIdentifier(ModuleRevisionId moduleRevisionId) {
        return new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    private void assertResolved() {
        assertHasResult();
        if (failure != null) {
            throw failure;
        }
    }

    private void assertHasResult() {
        if (failure == null && moduleDescriptor == null) {
            throw new IllegalStateException("No result has been specified.");
        }
    }
}
