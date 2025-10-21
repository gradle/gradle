/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyArtifactSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.internal.Try;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Default implementation of {@link DependencySubstitutionApplicator}, which caches results of
 * executing substitution rules.
 */
public class DefaultDependencySubstitutionApplicator implements DependencySubstitutionApplicator {

    private final InMemoryLoadingCache<SubstitutionCacheKey, Try<SubstitutionResult>> cache;

    public DefaultDependencySubstitutionApplicator(
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        Action<? super DependencySubstitutionInternal> rule,
        InstantiatorFactory instantiatorFactory,
        InMemoryCacheFactory cacheFactory
    ) {
        InstanceFactory<DefaultDependencySubstitution> substitutionFactory =
            instantiatorFactory.decorateScheme().forType(DefaultDependencySubstitution.class);

        this.cache = cacheFactory.create(key -> Try.ofFailable(() -> executeSubstitutionRule(
            substitutionFactory,
            componentSelectionDescriptorFactory,
            key,
            rule
        )));
    }

    @Override
    public DependencyState applySubstitutions(DependencyMetadata metadata) {
        SubstitutionCacheKey key = new SubstitutionCacheKey(
            metadata.getSelector(),
            // TODO: Ideally DependencyMetadata would already provide an ImmutableList of artifacts
            ImmutableList.copyOf(metadata.getArtifacts())
        );

        return cache.get(key)
            .map(result -> createDependencyStateFromSubstitution(metadata, result))
            .getOrMapFailure(failure -> createDependencyStateFromFailure(metadata, failure));
    }

    private static SubstitutionResult executeSubstitutionRule(
        InstanceFactory<DefaultDependencySubstitution> substitutionFactory,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        SubstitutionCacheKey requested,
        Action<? super DependencySubstitutionInternal> rule
    ) {
        DependencySubstitutionInternal details = substitutionFactory.newInstance(
            componentSelectionDescriptorFactory,
            requested.target,
            requested.artifacts
        );
        rule.execute(details);
        return SubstitutionResult.of(requested, details);
    }

    /**
     * Applies the given substitution result to the given dependency metadata, producing a dependency state
     * that incorporates the original request and the result of substitution.
     */
    private static DependencyState createDependencyStateFromSubstitution(DependencyMetadata metadata, SubstitutionResult result) {
        ComponentSelector target = result.target;
        ImmutableList<IvyArtifactName> artifacts = result.artifacts;

        DependencyMetadata updatedMetadata = metadata;
        if (target != null || artifacts != null) {
            ComponentSelector actualTarget = target != null ? target : metadata.getSelector();
            updatedMetadata = artifacts == null
                ? metadata.withTarget(actualTarget)
                : metadata.withTargetAndArtifacts(actualTarget, artifacts);
        }

        return new DependencyState(updatedMetadata, metadata.getSelector(), result.ruleDescriptors, null);
    }

    /**
     * Creates a dependency state representing a failed application of substitution rules to the given dependency metadata.
     */
    private static DependencyState createDependencyStateFromFailure(DependencyMetadata metadata, Throwable failure) {
        ModuleVersionResolveException resolveFailure = new ModuleVersionResolveException(metadata.getSelector(), failure);
        return new DependencyState(metadata, metadata.getSelector(), ImmutableList.of(), resolveFailure);
    }

    /**
     * Represents all information from a dependency metadata required for executing substitution rules.
     */
    private static class SubstitutionCacheKey {

        private final ComponentSelector target;
        private final ImmutableList<IvyArtifactName> artifacts;
        private final int hashCode;

        public SubstitutionCacheKey(ComponentSelector target, ImmutableList<IvyArtifactName> artifacts) {
            this.target = target;
            this.artifacts = artifacts;
            this.hashCode = computeHashCode(artifacts, target);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SubstitutionCacheKey that = (SubstitutionCacheKey) o;
            return target.equals(that.target) &&
                artifacts.equals(that.artifacts);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static int computeHashCode(List<IvyArtifactName> artifacts, ComponentSelector selector) {
            int result = selector.hashCode();
            result = 31 * result + artifacts.hashCode();
            return result;
        }

    }

    /**
     * Represents the result of successfully applying substitution rules to a dependency metadata.
     */
    private static class SubstitutionResult {

        private static final SubstitutionResult NO_OP = new SubstitutionResult(null, null, ImmutableList.of());

        private final @Nullable ComponentSelector target;
        private final @Nullable ImmutableList<IvyArtifactName> artifacts;
        private final ImmutableList<ComponentSelectionDescriptorInternal> ruleDescriptors;

        public SubstitutionResult(
            @Nullable ComponentSelector target,
            @Nullable ImmutableList<IvyArtifactName> artifacts,
            ImmutableList<ComponentSelectionDescriptorInternal> ruleDescriptors
        ) {
            this.target = target;
            this.artifacts = artifacts;
            this.ruleDescriptors = ruleDescriptors;
        }

        /**
         * Given a substitution details that has been configured by the user action, creates a
         * substitution result representing the configured results of the action.
         */
        public static SubstitutionResult of(SubstitutionCacheKey requested, DependencySubstitutionInternal details) {
            ComponentSelector target = details.getConfiguredTargetSelector();
            ImmutableList<DependencyArtifactSelector> artifacts = details.getConfiguredArtifactSelectors();

            if (target == null && artifacts == null) {
                return SubstitutionResult.NO_OP;
            }

            ImmutableList<ComponentSelectionDescriptorInternal> descriptors = details.getRuleDescriptors();
            assert descriptors != null && !descriptors.isEmpty();

            ImmutableList<IvyArtifactName> artifactNames = null;
            if (artifacts != null) {
                artifactNames = toIvyArtifactNames(target, requested.target, artifacts);
            }

            return new SubstitutionResult(target, artifactNames, descriptors);
        }

        private static ImmutableList<IvyArtifactName> toIvyArtifactNames(
            @Nullable ComponentSelector configuredTarget,
            ComponentSelector requestedTarget,
            ImmutableList<DependencyArtifactSelector> artifacts
        ) {
            if (artifacts.isEmpty()) {
                return ImmutableList.of();
            }

            ComponentSelector actualTarget = configuredTarget != null ? configuredTarget : requestedTarget;
            String targetModuleName = getModuleName(actualTarget);
            ImmutableList.Builder<IvyArtifactName> artifactsBuilder = ImmutableList.builderWithExpectedSize(artifacts.size());
            for (DependencyArtifactSelector das : artifacts) {
                artifactsBuilder.add(new DefaultIvyArtifactName(
                    targetModuleName,
                    das.getType(),
                    das.getExtension() != null ? das.getExtension() : das.getType(),
                    das.getClassifier()
                ));
            }
            return artifactsBuilder.build();
        }

        private static String getModuleName(ComponentSelector target) {
            if (!(target instanceof ModuleComponentSelector)) {
                throw new IllegalStateException("Substitution with artifacts for something else than a module is not supported");
            }
            return ((ModuleComponentSelector) target).getModule();
        }

    }

}
