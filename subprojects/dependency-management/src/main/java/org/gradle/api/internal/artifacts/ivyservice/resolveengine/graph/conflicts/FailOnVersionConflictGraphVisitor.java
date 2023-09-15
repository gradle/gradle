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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;

import java.util.Collections;
import java.util.Set;

/**
 * A visitor which batches up all conflicts and reports them all at once at the end of
 * the resolution.
 */
public class FailOnVersionConflictGraphVisitor implements DependencyGraphVisitor {

    private final Set<Conflict> allConflicts = Sets.newLinkedHashSet();
    private final String projectPath;
    private final String configurationName;

    public FailOnVersionConflictGraphVisitor(String projectPath, String configurationName) {
        this.projectPath = projectPath;
        this.configurationName = configurationName;
    }

    @Override
    public void start(RootGraphNode root) {

    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        DependencyGraphComponent owner = node.getOwner();
        ComponentSelectionReason selectionReason = owner.getSelectionReason();
        if (selectionReason.isConflictResolution()) {
            allConflicts.add(buildConflict(owner, selectionReason));
        }
    }

//    private Pair<List<? extends ModuleVersionIdentifier>, String> buildConflict(DependencyGraphComponent owner, ComponentSelectionReason selectionReason) {
    private Conflict buildConflict(DependencyGraphComponent owner, ComponentSelectionReason selectionReason) {
        ModuleIdentifier module = owner.getModuleVersion().getModule();
        return new Conflict(ImmutableList.copyOf(owner.getAllVersions()), buildConflictMessage(module, selectionReason));
    }

    private String buildConflictMessage(ModuleIdentifier owner, ComponentSelectionReason selectionReason) {
        String conflictDescription = null;
        for (ComponentSelectionDescriptor description : selectionReason.getDescriptions()) {
            if (description.getCause().equals(ComponentSelectionCause.CONFLICT_RESOLUTION)) {
                conflictDescription = description.getDescription();
            }
        }
        assert conflictDescription != null;
        return owner.getGroup() + ":" + owner.getName() + " " + conflictDescription;
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {

    }

    @Override
    public void visitEdges(DependencyGraphNode node) {

    }

    @Override
    public void finish(DependencyGraphNode root) {

    }

    public Set<Throwable> collectConflictFailures() {
        if (!allConflicts.isEmpty()) {
            return Collections.singleton(VersionConflictException.create(projectPath, configurationName, allConflicts));
        }
        return Collections.emptySet();
    }
}
