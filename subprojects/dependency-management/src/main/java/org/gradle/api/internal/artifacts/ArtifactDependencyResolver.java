/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.List;

public interface ArtifactDependencyResolver {
    void resolve(ResolveContext resolveContext,
                 List<? extends ResolutionAwareRepository> repositories,
                 GlobalDependencyResolutionRules metadataHandler,
                 Spec<? super DependencyMetadata> edgeFilter,
                 DependencyGraphVisitor graphVisitor,
                 DependencyArtifactsVisitor artifactsVisitor,
                 AttributesSchemaInternal consumerSchema,
                 ArtifactTypeRegistry artifactTypeRegistry,
                 boolean includeSyntheticDependencies);
}
