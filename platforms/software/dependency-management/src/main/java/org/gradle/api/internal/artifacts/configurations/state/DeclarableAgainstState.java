/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations.state;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.artifacts.DefaultDependencyConstraintSet;
import org.gradle.api.internal.artifacts.DefaultDependencySet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.ImmutableActionSet;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class DeclarableAgainstState {
    public ImmutableActionSet<DependencySet> defaultDependencyActions = ImmutableActionSet.empty();

    public final DefaultDependencySet dependencies;
    public final DefaultDomainObjectSet<Dependency> ownDependencies;

    public final DefaultDependencyConstraintSet dependencyConstraints;
    public final DefaultDomainObjectSet<DependencyConstraint> ownDependencyConstraints;

    public @Nullable DefaultDependencySet allDependencies;
    public @Nullable DefaultDependencyConstraintSet allDependencyConstraints;

    public List<String> declarationAlternatives = ImmutableList.of();

    public DeclarableAgainstState(ConfigurationInternal configuration, Action<String> ownDependenciesMutationValidator, DisplayName displayName, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        this.ownDependencies = (DefaultDomainObjectSet<Dependency>) domainObjectCollectionFactory.newDomainObjectSet(Dependency.class);
        this.ownDependencies.beforeCollectionChanges(ownDependenciesMutationValidator);
        this.dependencies = new DefaultDependencySet(Describables.of(displayName, "dependencies"), configuration, ownDependencies);

        this.ownDependencyConstraints = (DefaultDomainObjectSet<DependencyConstraint>) domainObjectCollectionFactory.newDomainObjectSet(DependencyConstraint.class);
        this.ownDependencyConstraints.beforeCollectionChanges(ownDependenciesMutationValidator);
        this.dependencyConstraints = new DefaultDependencyConstraintSet(Describables.of(displayName, "dependency constraints"), configuration, ownDependencyConstraints);
    }
}
