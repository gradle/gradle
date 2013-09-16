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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DefaultDependencyMetaData;
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;

import java.io.File;

public abstract class AbstractDescriptorParseContext implements DescriptorParseContext {

    protected LocallyAvailableExternalResource resolveArtifact(Artifact artifact, DependencyToModuleVersionResolver resolver) {
        File resolvedArtifactFile = resolveArtifactFile(artifact, resolver);
        LocallyAvailableResource localResource = new DefaultLocallyAvailableResource(resolvedArtifactFile);
        return new DefaultLocallyAvailableExternalResource(resolvedArtifactFile.toURI().toString(), localResource);
    }

    private File resolveArtifactFile(Artifact artifact, DependencyToModuleVersionResolver resolver) {
        BuildableArtifactResolveResult artifactResolveResult = new DefaultBuildableArtifactResolveResult();
        resolveModuleVersionResolveResult(artifact, resolver).getArtifactResolver().resolve(artifact, artifactResolveResult);
        return artifactResolveResult.getFile();
    }

    private BuildableModuleVersionResolveResult resolveModuleVersionResolveResult(Artifact artifact, DependencyToModuleVersionResolver resolver) {
        BuildableModuleVersionResolveResult moduleVersionResolveResult = new DefaultBuildableModuleVersionResolveResult();
        resolver.resolve(new DefaultDependencyMetaData(new DefaultDependencyDescriptor(artifact.getModuleRevisionId(), true)), moduleVersionResolveResult);
        return moduleVersionResolveResult;
    }
}
