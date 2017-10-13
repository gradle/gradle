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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;

import java.util.List;

/**
 * This class is responsible for maintaining the state of optional dependencies which are "pending". In other words, when an optional dependency is added to the graph, it is "pending" until a hard
 * dependency for the same module is seen. As soon as a hard dependency is found, nodes that referred to the optional dependency are restarted.
 *
 * This class also makes a special case of the "optional" configuration, for backwards compatibility: the optional configuration used to store optional dependencies. But if we no longer resolve
 * optional dependencies, then the optional configuration becomes effectively empty. To avoid this, we ignore the state of optional dependencies if they belong to the "optional" configuration.
 */
public class OptionalDependenciesHandler {
    private final OptionalDependenciesState optionalDependencies;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final DependencySubstitutionApplicator dependencySubstitutionApplicator;

    private boolean started;
    private boolean isOptionalConfiguration;
    private List<PendingOptionalDependencies> noLongerOptional;

    public OptionalDependenciesHandler(OptionalDependenciesState optionalDependencies,
                                       ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                       DependencySubstitutionApplicator dependencySubstitutionApplicator) {

        this.optionalDependencies = optionalDependencies;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.dependencySubstitutionApplicator = dependencySubstitutionApplicator;
    }

    private ModuleIdentifier lookupModuleIdentifier(DependencyState dependencyState) {
        DependencySubstitutionApplicator.SubstitutionResult substitutionResult = dependencySubstitutionApplicator.apply(dependencyState.getDependencyMetadata());
        DependencySubstitutionInternal details = substitutionResult.getResult();
        if (details != null && details.isUpdated()) {
            ComponentSelector target = details.getTarget();
            if (target instanceof ModuleComponentSelector) {
                ModuleComponentSelector selector = (ModuleComponentSelector) target;
                return moduleIdentifierFactory.module(selector.getGroup(), selector.getModule());
            }
        }
        return dependencyState.getModuleIdentifier();
    }

    boolean maybeAddAsOptionalDependency(NodeState node, DependencyState dependencyState) {
        ModuleIdentifier key = lookupModuleIdentifier(dependencyState);
        PendingOptionalDependencies pendingOptionalDependencies = optionalDependencies.getPendingOptionalDependencies(key);
        boolean pending = pendingOptionalDependencies.isPending();

        if (dependencyState.getDependencyMetadata().isOptional() && !isOptionalConfiguration && pending) {
                pendingOptionalDependencies.addNode(node);
                return true;
        }
        if (pending) {
            if (noLongerOptional == null) {
                noLongerOptional = Lists.newLinkedList();
            }
            noLongerOptional.add(pendingOptionalDependencies);
        }
        optionalDependencies.notOptional(key);
        return false;
    }

    public void start(boolean isOptionalConfiguration) {
        if (started) {
            throw new IllegalStateException("Cannot start optional dependencies handling: already started");
        }
        this.isOptionalConfiguration = isOptionalConfiguration;
        started = true;
    }

    public void complete() {
        if (noLongerOptional != null) {
            for (PendingOptionalDependencies optionalDependencies : noLongerOptional) {
                optionalDependencies.turnIntoHardDependencies();
            }
            noLongerOptional = null;
        }
        started = false;
    }
}
