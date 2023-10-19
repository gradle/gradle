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

package org.gradle.internal.component.model;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyConstraintsMetadata;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintsMetadataAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependenciesMetadataAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataAdapter;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.internal.CollectionUtils;

import java.util.List;

/**
 * A set of rules provided by the build script author
 * (as {@link Action<DirectDependenciesMetadata>} or {@link Action<DependencyConstraintsMetadata>})
 * that are applied on the dependencies defined in variant/configuration metadata. The rules are applied
 * in the {@link #execute(VariantResolveMetadata, List)} method when the dependencies of a variant are needed during dependency resolution.
 */
public class DependencyMetadataRules {
    private static final Spec<ModuleDependencyMetadata> DEPENDENCY_FILTER = dep -> !dep.isConstraint();
    private static final Spec<ModuleDependencyMetadata> DEPENDENCY_CONSTRAINT_FILTER = DependencyMetadata::isConstraint;

    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser;
    private final List<VariantMetadataRules.VariantAction<? super DirectDependenciesMetadata>> dependencyActions = Lists.newArrayList();
    private final List<VariantMetadataRules.VariantAction<? super DependencyConstraintsMetadata>> dependencyConstraintActions = Lists.newArrayList();
    private final ImmutableAttributesFactory attributesFactory;

    public DependencyMetadataRules(Instantiator instantiator,
                                   NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser,
                                   NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser,
                                   ImmutableAttributesFactory attributesFactory) {
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
        this.dependencyConstraintNotationParser = dependencyConstraintNotationParser;
        this.attributesFactory = attributesFactory;
    }

    public void addDependencyAction(VariantMetadataRules.VariantAction<? super DirectDependenciesMetadata> action) {
        dependencyActions.add(action);
    }

    public void addDependencyConstraintAction(VariantMetadataRules.VariantAction<? super DependencyConstraintsMetadata> action) {
        dependencyConstraintActions.add(action);
    }

    public <T extends ModuleDependencyMetadata> List<? extends ModuleDependencyMetadata> execute(VariantResolveMetadata variant, List<T> dependencies) {
        ImmutableList.Builder<ModuleDependencyMetadata> calculatedDependencies = new ImmutableList.Builder<>();
        calculatedDependencies.addAll(executeDependencyRules(variant, dependencies));
        calculatedDependencies.addAll(executeDependencyConstraintRules(variant, dependencies));
        return calculatedDependencies.build();
    }

    private <T extends ModuleDependencyMetadata> List<? extends ModuleDependencyMetadata> executeDependencyRules(VariantResolveMetadata variant, List<T> dependencies) {
        if (dependencyActions.isEmpty()) {
            return CollectionUtils.filter(dependencies, DEPENDENCY_FILTER);
        }

        DirectDependenciesMetadataAdapter adapter = instantiator.newInstance(
            DirectDependenciesMetadataAdapter.class, attributesFactory, instantiator, dependencyNotationParser);
        CollectionUtils.filter(dependencies, DEPENDENCY_FILTER).forEach(dep ->
            adapter.add(instantiator.newInstance(DirectDependencyMetadataAdapter.class, attributesFactory, dep)));

        dependencyActions.forEach(action -> action.maybeExecute(variant, adapter));

        return adapter.getMetadatas();
    }

    private <T extends ModuleDependencyMetadata> List<? extends ModuleDependencyMetadata> executeDependencyConstraintRules(VariantResolveMetadata variant, List<T> dependencies) {
        if (dependencyConstraintActions.isEmpty()) {
            return CollectionUtils.filter(dependencies, DEPENDENCY_CONSTRAINT_FILTER);
        }

        DependencyConstraintsMetadataAdapter adapter = instantiator.newInstance(
            DependencyConstraintsMetadataAdapter.class, attributesFactory, instantiator, dependencyConstraintNotationParser);

        CollectionUtils.filter(dependencies, DEPENDENCY_CONSTRAINT_FILTER).forEach(dep ->
            adapter.add(instantiator.newInstance(DependencyConstraintMetadataAdapter.class, attributesFactory, dep)));

        dependencyConstraintActions.forEach(action -> action.maybeExecute(variant, adapter));

        return adapter.getMetadatas();
    }
}
