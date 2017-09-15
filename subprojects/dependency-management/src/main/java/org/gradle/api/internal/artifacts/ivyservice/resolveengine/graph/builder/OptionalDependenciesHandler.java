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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.List;
import java.util.Map;

/**
 * This class is responsible for maintaining the state of optional dependencies which are "pending".
 * In other words, when an optional dependency is added to the graph, it is "pending" until a hard
 * dependency for the same module is seen. As soon as a hard dependency is found, nodes that referred
 * to the optional dependency are restarted.
 *
 * This class also makes a special case of the "optional" configuration, for backwards compatibility:
 * the optional configuration used to store optional dependencies. But if we no longer resolve optional
 * dependencies, then the optional configuration becomes effectively empty. To avoid this, we ignore the
 * state of optional dependencies if they belong to the "optional" configuration.
 */
public class OptionalDependenciesHandler {
    private final Map<ModuleIdentifier, PendingOptionalDependencies> optionalDependencies;
    private final boolean isOptionalConfiguration;
    List<PendingOptionalDependencies> noLongerOptional;

    public OptionalDependenciesHandler(Map<ModuleIdentifier, PendingOptionalDependencies> optionalDependencies, boolean isOptionalConfiguration) {

        this.optionalDependencies = optionalDependencies;
        this.isOptionalConfiguration = isOptionalConfiguration;
    }

    boolean maybeAddAsOptionalDependency(NodeState node, DependencyMetadata dependency) {
        ModuleVersionSelector requested = dependency.getRequested();
        ModuleIdentifier key = DefaultModuleIdentifier.newId(
            requested.getGroup(),
            requested.getName()
        );
        PendingOptionalDependencies pendingOptionalDependencies = optionalDependencies.get(key);
        if (dependency.isOptional() && !isOptionalConfiguration) {
            if (pendingOptionalDependencies == null || pendingOptionalDependencies.isPending()) {
                if (pendingOptionalDependencies == null) {
                    pendingOptionalDependencies = new PendingOptionalDependencies();
                    optionalDependencies.put(key, pendingOptionalDependencies);
                }
                pendingOptionalDependencies.addNode(node);
                return true;
            }
        } else if (pendingOptionalDependencies != null && pendingOptionalDependencies.isPending()) {
            if (noLongerOptional == null) {
                noLongerOptional = Lists.newLinkedList();
            }
            noLongerOptional.add(pendingOptionalDependencies);
        }
        optionalDependencies.put(key, PendingOptionalDependencies.NOT_OPTIONAL);
        return false;
    }

    public void complete() {
        if (noLongerOptional != null) {
            for (PendingOptionalDependencies optionalDependencies : noLongerOptional) {
                optionalDependencies.turnIntoHardDependencies();
            }
        }
    }
}
