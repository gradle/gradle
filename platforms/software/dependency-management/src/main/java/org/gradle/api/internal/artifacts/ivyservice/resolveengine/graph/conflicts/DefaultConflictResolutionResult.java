/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.NodeState;

import java.util.Collection;
import java.util.stream.Collectors;

class DefaultConflictResolutionResult implements ConflictResolutionResult {

    private static ComponentState findComponent(Object selected) {
        if (selected instanceof ComponentState) {
            return (ComponentState) selected;
        }
        if (selected instanceof NodeState) {
            return ((NodeState) selected).getComponent();
        }
        throw new IllegalArgumentException("Cannot extract a ComponentState from " + selected.getClass());
    }


    private final Collection<? extends ModuleIdentifier> participatingModules;
    private final ComponentState selected;

    public DefaultConflictResolutionResult(Collection<? extends ModuleIdentifier> participatingModules, Object selected) {
        this.selected = findComponent(selected);
        this.participatingModules = participatingModules.stream().sorted((first, second) -> {
            if (this.selected.getId().getModule().equals(first)) {
                return -1;
            } else if (this.selected.getId().getModule().equals(second)) {
                return 1;
            }
            return 0;
        }).collect(Collectors.toList());
    }

    @Override
    public void withParticipatingModules(Action<? super ModuleIdentifier> action) {
        for (ModuleIdentifier module : participatingModules) {
            action.execute(module);
        }
    }

    @Override
    public ComponentState getSelected() {
        return selected;
    }

}
