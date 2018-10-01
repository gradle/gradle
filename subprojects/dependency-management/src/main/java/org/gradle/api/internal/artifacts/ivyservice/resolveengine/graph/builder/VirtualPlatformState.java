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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class VirtualPlatformState {
    private final Comparator<String> vC;
    private final ModuleResolveState platformModule;

    private final Set<ModuleResolveState> participatingModules = Sets.newHashSet();
    private final List<EdgeState> orphanEdges = Lists.newArrayListWithExpectedSize(2);

    public VirtualPlatformState(final Comparator<Version> versionComparator, final VersionParser versionParser, ModuleResolveState platformModule) {
        this.vC = new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return versionComparator.compare(versionParser.transform(o2), versionParser.transform(o1));
            }
        };
        this.platformModule = platformModule;
    }

    void participatingModule(ModuleResolveState state) {
        if (participatingModules.add(state)) {
            ComponentState selected = platformModule.getSelected();
            if (selected != null) {
                // There is a possibility that a platform version was selected before a new member
                // of the platform was discovered. In this case, we need to restart the selection,
                // or some members will not be upgraded
                for (NodeState nodeState : selected.getNodes()) {
                    nodeState.resetSelectionState();
                }
            }
        }
    }

    List<String> getCandidateVersions() {
        ComponentState selectedPlatformComponent = platformModule.getSelected();
        if (selectedPlatformComponent.getSelectionReason().isForced()) {
            return Collections.singletonList(selectedPlatformComponent.getVersion());
        }
        List<String> sorted = Lists.newArrayListWithCapacity(participatingModules.size());
        for (ModuleResolveState module : participatingModules) {
            ComponentState selected = module.getSelected();
            if (selected != null) {
                if (selected.getSelectionReason().isForced()) {
                    return Collections.singletonList(selected.getVersion());
                }
                sorted.add(selected.getVersion());
            }
        }
        Collections.sort(sorted, vC);
        return sorted;
    }

    Set<ModuleResolveState> getParticipatingModules() {
        return participatingModules;
    }

    ComponentIdentifier getSelectedPlatformId() {
        ComponentState selected = platformModule.getSelected();
        if (selected != null) {
            return selected.getComponentId();
        }
        return null;
    }

    boolean isForced() {
        for (ModuleResolveState participatingModule : participatingModules) {
            ComponentState selected = participatingModule.getSelected();
            if (selected != null && selected.getSelectionReason().isForced()) {
                return true;
            }
        }
        return platformModule.getSelected().getSelectionReason().isForced();
    }

    /**
     * It is possible that a member of a virtual platform is discovered after trying
     * to resolve the platform itself. If the platform was declared as a dependency,
     * then the engine thinks that the platform module is unresolved. We need to
     * remember such edges, because in case a virtual platform gets defined, the error
     * is no longer valid and we can attach the target revision.
     * @param edge the orphan edge
     */
    void addOrphanEdge(EdgeState edge) {
        orphanEdges.add(edge);
    }

    void attachOrphanEdges() {
        for (EdgeState orphanEdge : orphanEdges) {
            orphanEdge.attachToTargetConfigurations();
        }
        orphanEdges.clear();
    }
}
