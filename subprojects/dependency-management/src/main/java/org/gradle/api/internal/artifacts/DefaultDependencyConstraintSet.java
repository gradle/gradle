/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.DependencyConstraintSet;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.deprecation.DeprecationLogger;

import java.util.Collection;
import java.util.List;

public class DefaultDependencyConstraintSet extends DelegatingDomainObjectSet<DependencyConstraint> implements DependencyConstraintSet {
    private final Describable displayName;
    private final ConfigurationInternal clientConfiguration;

    public DefaultDependencyConstraintSet(Describable displayName, ConfigurationInternal clientConfiguration, DomainObjectSet<DependencyConstraint> backingSet) {
        super(backingSet);
        this.displayName = displayName;
        this.clientConfiguration = clientConfiguration;
    }

    @Override
    public String toString() {
        return displayName.getDisplayName();
    }

    @Override
    public boolean add(final DependencyConstraint dependencyConstraint) {
        assertConfigurationIsDeclarableAgainst();
        warnIfConfigurationIsDeprecated();
        return addInternalDependencyConstraint(dependencyConstraint);
    }

    // For internal use only, allows adding a dependency constraint without issuing a deprecation warning
    public boolean addInternalDependencyConstraint(DependencyConstraint dependencyConstraint) {
        return super.add(dependencyConstraint);
    }

    private void warnIfConfigurationIsDeprecated() {
        List<String> alternatives = ((DeprecatableConfiguration) clientConfiguration).getDeclarationAlternatives();
        if (alternatives != null) {
            DeprecationLogger.deprecateConfiguration(clientConfiguration.getName()).forDependencyDeclaration().replaceWith(alternatives)
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }
    }

    private void assertConfigurationIsDeclarableAgainst() {
        if (!clientConfiguration.isCanBeDeclaredAgainst()) {
            throw new GradleException("Dependency constraints can not be declared against the `" + clientConfiguration.getName() + "` configuration.");
        }
    }

    @Override
    public boolean addAll(Collection<? extends DependencyConstraint> dependencyConstraints) {
        boolean added = false;
        for (DependencyConstraint dependencyConstraint : dependencyConstraints) {
            added |= add(dependencyConstraint);
        }
        return added;
    }
}
