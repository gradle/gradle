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
package org.gradle.api.internal.artifacts;

import org.gradle.api.Describable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.dependencies.AbstractModuleDependency;
import org.gradle.api.tasks.TaskDependency;

import java.util.Collection;

public class DefaultDependencySet extends DelegatingDomainObjectSet<Dependency> implements DependencySet {
    private final Describable displayName;
    private final ConfigurationInternal clientConfiguration;

    public DefaultDependencySet(Describable displayName, final ConfigurationInternal clientConfiguration, DomainObjectSet<Dependency> backingSet) {
        super(backingSet);
        this.displayName = displayName;
        this.clientConfiguration = clientConfiguration;
    }

    @Override
    public String toString() {
        return displayName.getDisplayName();
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return clientConfiguration.getBuildDependencies();
    }

    @Override
    public boolean add(final Dependency o) {
        assertConfigurationIsDeclarable();
        clientConfiguration.maybeEmitDeclarationDeprecation();
        if (o instanceof AbstractModuleDependency) {
            ((AbstractModuleDependency) o).addMutationValidator(dep ->
                clientConfiguration.validateMutation(MutationValidator.MutationType.DEPENDENCY_ATTRIBUTES)
            );
        }
        return super.add(o);
    }

    private void assertConfigurationIsDeclarable() {
        if (!clientConfiguration.isCanBeDeclared()) {
            throw new GradleException("Dependencies can not be declared against the `" + clientConfiguration.getName() + "` configuration.");
        }
    }

    @Override
    public boolean addAll(Collection<? extends Dependency> dependencies) {
        boolean added = false;
        for (Dependency dependency : dependencies) {
            added |= add(dependency);
        }
        return added;
    }
}
