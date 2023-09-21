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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;

import java.util.List;

/**
 * Factory for dependency graph resolvers that can resolve {@link ResolveContext}s.
 */
public interface ResolverFactory {

    /**
     * Create a resolver that resolves the provided {@link ResolveContext} using the provided repositories.
     */
    Resolver create(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema,
        GlobalDependencyResolutionRules metadataHandler
    );

    /**
     * Represents a set of sources of component metadata and artifacts. These sources
     * can be used to resolve dependency graphs and resolve the artifacts of the
     * components in the graph.
     */
    interface Resolver {

        /**
         * Returns a resolver that can resolve artifacts given component metadata.
         */
        ArtifactResolver getArtifactResolver();

        /**
         * Perform a graph resolution, visiting the resolved graph with the provided visitors.
         */
        void resolveGraph(
            Spec<? super DependencyMetadata> edgeFilter,
            boolean includeSyntheticDependencies,
            List<DependencyGraphVisitor> visitors
        );
    }

}
