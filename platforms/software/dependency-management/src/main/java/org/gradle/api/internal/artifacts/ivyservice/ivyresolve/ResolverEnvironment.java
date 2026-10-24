/*
 * Copyright 2026 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ComponentSelection;
import org.gradle.api.artifacts.ComponentSelectionRules;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.dsl.ImmutableComponentMetadataRules;
import org.gradle.api.internal.artifacts.ivyservice.CacheExpirationControl;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.SpecRuleAction;

import java.util.Collection;
import java.util.List;

/**
 * All state required to create a set of external component resolvers.
 *
 * <p>Two environments that are equal produce equivalent resolver chains, which allows
 * {@link ExternalModuleComponentResolverFactory} to reuse resolver chains across resolutions
 * and projects:
 *
 * <ul>
 *     <li>Repositories are compared element-wise by identity. The repository container of a
 *     project (or the shared settings container) yields the same instances as long as it has
 *     not been mutated, and a mutation requires new resolvers anyway. The list itself is
 *     rebuilt for each resolution, so it must not be compared by identity.</li>
 *     <li>Component selection rules are snapshotted at construction time. The rules object
 *     passed to the constructor is live and per-configuration, so identity comparison would
 *     prevent reuse across configurations of the same project. Snapshots containing the same
 *     rule actions produce equivalent resolvers.</li>
 *     <li>All other inputs are immutable value objects.</li>
 * </ul>
 */
public record ResolverEnvironment(
    ImmutableList<ResolutionAwareRepository> repositories,
    ImmutableComponentMetadataRules componentMetadataRules,
    VariantDerivationStrategy variantDerivationStrategy,
    ImmutableList<SpecRuleAction<? super ComponentSelection>> componentSelectionRules,
    boolean dependencyVerificationEnabled,
    CacheExpirationControl cacheExpirationControl,
    ImmutableAttributesSchema consumerSchema
) {

    public ResolverEnvironment(
        List<? extends ResolutionAwareRepository> repositories,
        ImmutableComponentMetadataRules componentMetadataRules,
        VariantDerivationStrategy variantDerivationStrategy,
        ComponentSelectionRulesInternal componentSelectionRules,
        boolean dependencyVerificationEnabled,
        CacheExpirationControl cacheExpirationControl,
        ImmutableAttributesSchema consumerSchema
    ) {
        this(
            ImmutableList.copyOf(repositories),
            componentMetadataRules,
            variantDerivationStrategy,
            ImmutableList.copyOf(componentSelectionRules.getRules()),
            dependencyVerificationEnabled,
            cacheExpirationControl,
            consumerSchema
        );
    }

    /**
     * The component selection rules to apply during resolution, as an immutable snapshot
     * taken when this environment was created.
     */
    public ComponentSelectionRulesInternal getComponentSelectionRules() {
        return new ImmutableComponentSelectionRules(componentSelectionRules);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolverEnvironment that)) {
            return false;
        }
        return dependencyVerificationEnabled == that.dependencyVerificationEnabled
            && identityEquals(repositories, that.repositories)
            && componentMetadataRules.equals(that.componentMetadataRules)
            && variantDerivationStrategy.equals(that.variantDerivationStrategy)
            && componentSelectionRules.equals(that.componentSelectionRules)
            && cacheExpirationControl.equals(that.cacheExpirationControl)
            && consumerSchema.equals(that.consumerSchema);
    }

    @Override
    public int hashCode() {
        int result = identityHash(repositories);
        result = 31 * result + componentMetadataRules.hashCode();
        result = 31 * result + variantDerivationStrategy.hashCode();
        result = 31 * result + componentSelectionRules.hashCode();
        result = 31 * result + Boolean.hashCode(dependencyVerificationEnabled);
        result = 31 * result + cacheExpirationControl.hashCode();
        result = 31 * result + consumerSchema.hashCode();
        return result;
    }

    private static boolean identityEquals(List<ResolutionAwareRepository> left, List<ResolutionAwareRepository> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (left.get(i) != right.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static int identityHash(List<ResolutionAwareRepository> repositories) {
        int hash = 1;
        for (ResolutionAwareRepository repository : repositories) {
            hash = 31 * hash + System.identityHashCode(repository);
        }
        return hash;
    }

    private record ImmutableComponentSelectionRules(
        ImmutableList<SpecRuleAction<? super ComponentSelection>> rules
    ) implements ComponentSelectionRulesInternal {

        @Override
        public Collection<SpecRuleAction<? super ComponentSelection>> getRules() {
            return rules;
        }

        @Override
        public ComponentSelectionRules all(Action<? super ComponentSelection> selectionAction) {
            throw immutable();
        }

        @Override
        public ComponentSelectionRules all(Closure<?> closure) {
            throw immutable();
        }

        @Override
        @Deprecated
        public ComponentSelectionRules all(Object ruleSource) {
            throw immutable();
        }

        @Override
        public ComponentSelectionRules withModule(Object id, Action<? super ComponentSelection> selectionAction) {
            throw immutable();
        }

        @Override
        public ComponentSelectionRules withModule(Object id, Closure<?> closure) {
            throw immutable();
        }

        @Override
        @Deprecated
        public ComponentSelectionRules withModule(Object id, Object ruleSource) {
            throw immutable();
        }

        @Override
        public ComponentSelectionRules addRule(SpecRuleAction<? super ComponentSelection> specRuleAction) {
            throw immutable();
        }

        @Override
        public ComponentSelectionRules addRule(RuleAction<? super ComponentSelection> specRuleAction) {
            throw immutable();
        }

        private static UnsupportedOperationException immutable() {
            return new UnsupportedOperationException("Component selection rules of a resolver environment are immutable.");
        }

    }

}
