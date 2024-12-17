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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VirtualPlatformState {
    private final Comparator<String> vC;
    private final ModuleResolveState platformModule;
    private final ResolveOptimizations resolveOptimizations;

    private final Set<ModuleResolveState> participatingModules = new LinkedHashSet<>();
    private final List<EdgeState> orphanEdges = new ArrayList<>(2);

    private boolean hasForcedParticipatingModule;

    public VirtualPlatformState(final Comparator<Version> versionComparator, final VersionParser versionParser, ModuleResolveState platformModule, ResolveOptimizations resolveOptimizations) {
        this.vC = (o1, o2) -> versionComparator.compare(versionParser.transform(o2), versionParser.transform(o1));
        this.platformModule = platformModule;
        this.resolveOptimizations = resolveOptimizations;
    }

    void participatingModule(ModuleResolveState state) {
        state.registerPlatformOwner(this);
        if (participatingModules.add(state)) {
            resolveOptimizations.declareVirtualPlatformInUse();
            ComponentState selected = platformModule.getSelected();
            if (selected != null) {
                // There is a possibility that a platform version was selected before a new member
                // of the platform was discovered. In this case, we need to restart the selection,
                // or some members will not be upgraded
                for (NodeState nodeState : selected.getNodes()) {
                    nodeState.markForVirtualPlatformRefresh();
                }
            }
            hasForcedParticipatingModule |= isParticipatingModuleForced(state);
        }
    }

    @Nullable
    private String getForcedVersion() {
        String version = null;
        for (SelectorState selector : platformModule.getSelectors()) {
            if (selector.hasStrongOpinion()) {
                ComponentSelector rawSelector = selector.getComponentSelector();
                if (rawSelector instanceof ModuleComponentSelector) {
                    String nv = ((ModuleComponentSelector) rawSelector).getVersion();
                    if (version == null || vC.compare(nv, version) < 0) {
                        version = nv;
                    }
                }
            }
        }
        return version;
    }

    List<String> getCandidateVersions() {
        String forcedVersion = getForcedVersion();
        ComponentState selectedPlatformComponent = platformModule.getSelected();
        List<String> sorted = new ArrayList<>(participatingModules.size() + 1);
        sorted.add(selectedPlatformComponent.getVersion());
        for (ModuleResolveState module : participatingModules) {
            ComponentState selected = module.getSelected();
            if (selected != null) {
                sorted.add(selected.getVersion());
            }
        }
        sorted.sort(vC);
        if (forcedVersion != null) {
            return sorted.subList(sorted.indexOf(forcedVersion), sorted.size());
        } else {
            return sorted;
        }
    }

    Set<ModuleResolveState> getParticipatingModules() {
        return participatingModules;
    }

    @Nullable
    public ComponentIdentifier getSelectedPlatformId() {
        ComponentState selected = platformModule.getSelected();
        if (selected != null) {
            return selected.getComponentId();
        }
        return null;
    }

    boolean isForced() {
        return hasForcedParticipatingModule || isSelectedPlatformForced();
    }

    private boolean isSelectedPlatformForced() {
        boolean forced = platformModule.getSelected().hasStrongOpinion();
        if (forced) {
            resolveOptimizations.declareForcedPlatformInUse();
        }
        return forced;
    }

    private boolean isParticipatingModuleForced(ModuleResolveState participatingModule) {
        ComponentState selected = participatingModule.getSelected();
        boolean forced = selected != null && selected.hasStrongOpinion();
        if (forced) {
            resolveOptimizations.declareForcedPlatformInUse();
        }
        return forced;
    }

    /**
     * It is possible that a member of a virtual platform is discovered after trying
     * to resolve the platform itself. If the platform was declared as a dependency,
     * then the engine thinks that the platform module is unresolved. We need to
     * remember such edges, because in case a virtual platform gets defined, the error
     * is no longer valid and we can attach the target revision.
     *
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

    public boolean isGreaterThanForcedVersion(String version) {
        String forcedVersion = getForcedVersion();
        if (forcedVersion == null) {
            return false;
        }
        return vC.compare(forcedVersion, version) > 0;
    }
}
