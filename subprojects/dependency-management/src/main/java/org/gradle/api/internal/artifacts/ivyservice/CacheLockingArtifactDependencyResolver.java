/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.List;

public class CacheLockingArtifactDependencyResolver implements ArtifactDependencyResolver {
    private final CacheLockingManager lockingManager;
    private final ArtifactDependencyResolver resolver;

    public CacheLockingArtifactDependencyResolver(CacheLockingManager lockingManager, ArtifactDependencyResolver resolver) {
        this.lockingManager = lockingManager;
        this.resolver = resolver;
    }

    @Override
    public void resolve(final ResolveContext resolveContext, final List<? extends ResolutionAwareRepository> repositories, final GlobalDependencyResolutionRules metadataHandler, final Spec<? super DependencyMetadata> edgeFilter, final DependencyGraphVisitor graphVisitor, final DependencyArtifactsVisitor artifactsVisitor, final AttributesSchema attributesSchema) {
        lockingManager.useCache("resolve " + resolveContext, new Runnable() {
            public void run() {
                resolver.resolve(resolveContext, repositories, metadataHandler, edgeFilter, graphVisitor, artifactsVisitor, attributesSchema);
            }
        });
    }
}
