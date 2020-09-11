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
package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.transform.ExtraExecutionGraphDependenciesResolverFactory;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.util.Path;

import java.util.Collection;
import java.util.Set;

public interface ConfigurationInternal extends ResolveContext, Configuration, DeprecatableConfiguration, DependencyMetaDataProvider {
    enum InternalState {
        UNRESOLVED,
        BUILD_DEPENDENCIES_RESOLVED,
        GRAPH_RESOLVED,
        ARTIFACTS_RESOLVED
    }

    @Override
    ResolutionStrategyInternal getResolutionStrategy();

    @Override
    AttributeContainerInternal getAttributes();

    String getPath();

    Path getIdentityPath();

    /**
     * Runs any registered dependency actions for this Configuration, and any parent Configuration.
     * Actions may mutate the dependency set for this configuration.
     * After execution, all actions are de-registered, so execution will only occur once.
     */
    void runDependencyActions();

    void markAsObserved(InternalState requestedState);

    void addMutationValidator(MutationValidator validator);

    void removeMutationValidator(MutationValidator validator);

    /**
     * Converts this configuration to an {@link OutgoingVariant} view. The view may not necessarily be immutable.
     */
    OutgoingVariant convertToOutgoingVariant();

    /**
     * Visits the variants of this configuration.
     */
    void collectVariants(VariantVisitor visitor);

    /**
     * Registers an action to execute before locking for further mutation.
     */
    void beforeLocking(Action<? super ConfigurationInternal> action);

    void preventFromFurtherMutation();

    /**
     * Gets the complete set of exclude rules including those contributed by
     * superconfigurations.
     */
    Set<ExcludeRule> getAllExcludeRules();

    ExtraExecutionGraphDependenciesResolverFactory getDependenciesResolver();

    interface VariantVisitor {
        // The artifacts to use when this configuration is used as a configuration
        void visitArtifacts(Collection<? extends PublishArtifact> artifacts);

        // This configuration as a variant. May not always be present
        void visitOwnVariant(DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> artifacts);

        // A child variant. May not always be present
        void visitChildVariant(String name, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> artifacts);
    }
}
