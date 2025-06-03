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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.List;

/**
 * Resolves {@link ConfigurationInternal}s and produces {@link ResolverResults}.
 * <p>
 * This resolution is lenient, except for some fatal failure cases,
 * in the sense that resolution failures in most cases will not cause exceptions
 * to be thrown. Instead, recoverable failures are packaged in the result type.
 */
public interface ConfigurationResolver {
    /**
     * Traverses enough of the graph to calculate the build dependencies of the given configuration. All failures are packaged in the result.
     *
     * @param configuration The resolve context to resolve.
     * @param futureCompleteResults The future value of the output of {@link #resolveGraph(ConfigurationInternal)}. See
     * {@link DefaultTransformUpstreamDependenciesResolver} for why this is needed.
     */
    ResolverResults resolveBuildDependencies(ConfigurationInternal configuration, CalculatedValue<ResolverResults> futureCompleteResults);

    /**
     * Traverses the full dependency graph of the given configuration. All failures are packaged in the result.
     */
    ResolverResults resolveGraph(ConfigurationInternal configuration) throws ResolveException;

    /**
     * Returns the list of repositories available to resolve a given configuration.
     */
    List<ResolutionAwareRepository> getAllRepositories();

    /**
     * Creates {@link ConfigurationResolver} instances.
     * <p>
     * We require this factory since configuration resolvers within a project
     * need a reference to the configuration container, and the configuration container
     * needs a reference to the configuration resolver (since new configurations also
     * need a reference to the resolver).
     * <p>
     * In future versions, we will want to avoid this circular dependency by making
     * the root variant of a resolved configuration live in an adhoc root component,
     * just like all other resolved configurations.
     */
    @ServiceScope(Scope.Project.class)
    interface Factory {

        /**
         * Create a new configuration resolver, potentially using the given configurations
         * provider as a source of variants for the root component that will own the root
         * variant of the dependency graph.
         */
        ConfigurationResolver create(
            ConfigurationsProvider configurations,
            DomainObjectContext owner,
            AttributesSchemaInternal schema
        );

    }
}
