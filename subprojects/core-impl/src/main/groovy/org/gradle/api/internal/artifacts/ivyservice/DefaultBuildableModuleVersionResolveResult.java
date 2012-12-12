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

public class DefaultBuildableModuleVersionResolveResult implements BuildableModuleVersionResolveResult {
    private ModuleRevisionId moduleRevisionId;
    private ModuleDescriptor moduleDescriptor;
    private ModuleVersionResolveException failure;
    private ArtifactResolver artifactResolver;

    public DefaultBuildableModuleVersionResolveResult failed(ModuleVersionResolveException failure) {
        moduleDescriptor = null;
        this.failure = failure;
        return this;
    }

    public void notFound(ModuleRevisionId moduleRevisionId) {
        failed(new ModuleVersionNotFoundException(moduleRevisionId));
    }

    public void resolved(ModuleRevisionId moduleRevisionId, ModuleDescriptor descriptor, ArtifactResolver artifactResolver) {
        this.moduleRevisionId = moduleRevisionId;
        this.moduleDescriptor = descriptor;
        this.artifactResolver = artifactResolver;
    }

    public void setMetaData(ModuleRevisionId moduleRevisionId, ModuleDescriptor descriptor) {
        assertResolved();
        this.moduleRevisionId = moduleRevisionId;
        this.moduleDescriptor = descriptor;
    }

    public void setArtifactResolver(ArtifactResolver artifactResolver) {
        assertResolved();
        this.artifactResolver = artifactResolver;
    }

    public ModuleRevisionId getId() throws ModuleVersionResolveException {
        assertResolved();
        return moduleRevisionId;
    }

    public ModuleDescriptor getDescriptor() throws ModuleVersionResolveException {
        assertResolved();
        return moduleDescriptor;
    }

    public ArtifactResolver getArtifactResolver() throws ModuleVersionResolveException {
        assertResolved();
        return artifactResolver;
    }

    public ModuleVersionResolveException getFailure() {
        assertHasResult();
        return failure;
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
