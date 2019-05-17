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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictResolutionResult;

class ReplaceSelectionWithConflictResultAction implements Action<ConflictResolutionResult> {
    private final ResolveState resolveState;

    ReplaceSelectionWithConflictResultAction(ResolveState resolveState) {
        this.resolveState = resolveState;
    }

    @Override
    public void execute(final ConflictResolutionResult result) {
        Object selected = result.getSelected();
        final ComponentState component = findComponent(selected);
        result.withParticipatingModules(new Action<ModuleIdentifier>() {
            @Override
            public void execute(ModuleIdentifier moduleIdentifier) {
                // Restart each configuration. For the evicted configuration, this means moving incoming dependencies across to the
                // matching selected configuration. For the select configuration, this mean traversing its dependencies.
                resolveState.getModule(moduleIdentifier).restart(component);
            }
        });
    }

    private ComponentState findComponent(Object selected) {
        if (selected instanceof ComponentState) {
            return (ComponentState) selected;
        }
        if (selected instanceof NodeState) {
            return ((NodeState) selected).getComponent();
        }
        return null;
    }
}
