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

import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.ComponentUsage;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

public class ErrorHandlingArtifactResolver implements ArtifactResolver {
    private final ArtifactResolver resolver;

    public ErrorHandlingArtifactResolver(ArtifactResolver resolver) {
        this.resolver = resolver;
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        try {
            resolver.resolveModuleArtifacts(component, artifactType, result);
        } catch (Throwable t) {
            result.failed(new ArtifactResolveException(component.getComponentId(), t));
        }
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        try {
            resolver.resolveModuleArtifacts(component, usage, result);
        } catch (Throwable t) {
            result.failed(new ArtifactResolveException(component.getComponentId(), t));
        }
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        try {
            resolver.resolveArtifact(artifact, moduleSource, result);
        } catch (Throwable t) {
            result.failed(new ArtifactResolveException(artifact.getId(), t));
        }
    }
}
