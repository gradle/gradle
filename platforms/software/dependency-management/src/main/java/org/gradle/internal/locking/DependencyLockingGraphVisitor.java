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

package org.gradle.internal.locking;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.DefaultUnresolvedDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DependencyLockingGraphVisitor implements DependencyGraphVisitor {

    private final DependencyLockingProvider dependencyLockingProvider;
    private final String configurationName;
    private Set<ModuleComponentIdentifier> allResolvedModules;
    private Set<ModuleComponentIdentifier> changingResolvedModules;
    private Set<ModuleComponentIdentifier> extraModules;
    private Map<ModuleComponentIdentifier, String> forcedModules;
    private Map<ModuleIdentifier, ModuleComponentIdentifier> modulesToBeLocked;
    private DependencyLockingState dependencyLockingState;
    private boolean lockOutOfDate = false;

    public DependencyLockingGraphVisitor(String configurationName, DependencyLockingProvider dependencyLockingProvider) {
        this.configurationName = configurationName;
        this.dependencyLockingProvider = dependencyLockingProvider;
    }

    @Override
    public void start(RootGraphNode root) {
        dependencyLockingState = dependencyLockingProvider.loadLockState(configurationName);
        if (dependencyLockingState.mustValidateLockState()) {
            Set<ModuleComponentIdentifier> lockedModules = dependencyLockingState.getLockedDependencies();
            modulesToBeLocked = Maps.newHashMapWithExpectedSize(lockedModules.size());
            for (ModuleComponentIdentifier lockedModule : lockedModules) {
                modulesToBeLocked.put(lockedModule.getModuleIdentifier(), lockedModule);
            }
            allResolvedModules = Sets.newHashSetWithExpectedSize(this.modulesToBeLocked.size());
            extraModules = Sets.newHashSet();
            forcedModules = Maps.newHashMap();
        } else {
            modulesToBeLocked = Collections.emptyMap();
            allResolvedModules = Sets.newHashSet();
        }
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        boolean changing = false;
        ComponentIdentifier identifier = node.getOwner().getComponentId();
        ComponentGraphResolveMetadata metadata = node.getOwner().getMetadataOrNull();
        if (metadata != null && metadata.isChanging()) {
            changing = true;
        }
        if (!node.isRoot() && identifier instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier id = (ModuleComponentIdentifier) identifier;
            if (identifier instanceof MavenUniqueSnapshotComponentIdentifier) {
                id = ((MavenUniqueSnapshotComponentIdentifier) id).getSnapshotComponent();
            }
            if (!id.getVersion().isEmpty()) {
                if (allResolvedModules.add(id)) {
                    if (changing) {
                        addChangingModule(id);
                    }
                    if (dependencyLockingState.mustValidateLockState()) {
                        ModuleComponentIdentifier lockedId = modulesToBeLocked.remove(id.getModuleIdentifier());
                        if (lockedId == null) {
                            if (!dependencyLockingState.getIgnoredEntryFilter().isSatisfiedBy(id)) {
                                extraModules.add(id);
                            }
                        } else if (!lockedId.getVersion().equals(id.getVersion()) && !isNodeRejected(node)) {
                            // Need to check that versions do match, mismatch indicates a force was used
                            forcedModules.put(lockedId, id.getVersion());
                        }
                    }
                }
            }
        }
    }

    private boolean isNodeRejected(DependencyGraphNode node) {
        // That is the state a node is in when it was selected but the selection violates a constraint (reject or strictly)
        return node.getComponent().isRejected();
    }

    private void addChangingModule(ModuleComponentIdentifier id) {
        if (changingResolvedModules == null) {
            changingResolvedModules = new HashSet<>();
        }
        changingResolvedModules.add(id);
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {
        // No-op
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        // No-op
    }

    @Override
    public void finish(DependencyGraphNode root) {

    }

    public void writeLocks() {
        if (!lockOutOfDate) {
            Set<ModuleComponentIdentifier> changingModules = this.changingResolvedModules == null ? Collections.emptySet() : this.changingResolvedModules;
            dependencyLockingProvider.persistResolvedDependencies(configurationName, allResolvedModules, changingModules);
        }
    }

    /**
     * This will transform any lock out of date result into an {@link UnresolvedDependency} in order to plug into lenient resolution.
     * This happens only if there are no previous failures as otherwise lock state can't be asserted.
     *
     * @return the existing failures augmented with any locking related one
     */
    public Set<UnresolvedDependency> collectLockingFailures() {
        if (dependencyLockingState.mustValidateLockState()) {
            if (!modulesToBeLocked.isEmpty() || !extraModules.isEmpty() || !forcedModules.isEmpty()) {
                lockOutOfDate = true;
                return createLockingFailures(modulesToBeLocked, extraModules, forcedModules);
            }
        }
        return Collections.emptySet();
    }

    private static Set<UnresolvedDependency> createLockingFailures(Map<ModuleIdentifier, ModuleComponentIdentifier> modulesToBeLocked, Set<ModuleComponentIdentifier> extraModules, Map<ModuleComponentIdentifier, String> forcedModules) {
        Set<UnresolvedDependency> completedFailures = Sets.newHashSetWithExpectedSize(modulesToBeLocked.values().size() + extraModules.size());
        for (ModuleComponentIdentifier presentInLock : modulesToBeLocked.values()) {
            completedFailures.add(new DefaultUnresolvedDependency(DefaultModuleVersionSelector.newSelector(presentInLock.getModuleIdentifier(), presentInLock.getVersion()),
                                  new LockOutOfDateException("Did not resolve '" + presentInLock.getDisplayName() + "' which is part of the dependency lock state")));
        }
        for (ModuleComponentIdentifier extraModule : extraModules) {
            completedFailures.add(new DefaultUnresolvedDependency(DefaultModuleVersionSelector.newSelector(extraModule.getModuleIdentifier(), extraModule.getVersion()),
                new LockOutOfDateException("Resolved '" + extraModule.getDisplayName() + "' which is not part of the dependency lock state")));
        }
        for (Map.Entry<ModuleComponentIdentifier, String> entry : forcedModules.entrySet()) {
            ModuleComponentIdentifier forcedModule = entry.getKey();
            completedFailures.add(new DefaultUnresolvedDependency(DefaultModuleVersionSelector.newSelector(forcedModule.getModuleIdentifier(), forcedModule.getVersion()),
                new LockOutOfDateException("Did not resolve '" + forcedModule.getDisplayName() + "' which has been forced / substituted to a different version: '" + entry.getValue() + "'")));
        }
        return completedFailures;
    }
}
