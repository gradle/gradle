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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CandidateModule;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolution state for a given module.
 */
class ModuleResolveState implements CandidateModule {
    private final ComponentMetaDataResolver metaDataResolver;
    private final IdGenerator<Long> idGenerator;
    private final ModuleIdentifier id;
    private final List<EdgeState> unattachedDependencies = new LinkedList<EdgeState>();
    private final Map<ModuleVersionIdentifier, ComponentState> versions = new LinkedHashMap<ModuleVersionIdentifier, ComponentState>();
    private final Set<SelectorState> selectors = new HashSet<SelectorState>();
    private ComponentState selected;

    ModuleResolveState(IdGenerator<Long> idGenerator, ModuleIdentifier id, ComponentMetaDataResolver metaDataResolver) {
        this.idGenerator = idGenerator;
        this.id = id;
        this.metaDataResolver = metaDataResolver;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public ModuleIdentifier getId() {
        return id;
    }

    @Override
    public Collection<ComponentState> getVersions() {
        if (this.versions.isEmpty()) {
            return Collections.emptyList();
        }
        Collection<ComponentState> values = this.versions.values();
        if (areAllCandidatesForSelection(values)) {
            return values;
        }
        List<ComponentState> versions = Lists.newArrayListWithCapacity(values.size());
        for (ComponentState componentState : values) {
            if (componentState.isCandidateForConflictResolution()) {
                versions.add(componentState);
            }
        }
        return versions;
    }

    private static boolean areAllCandidatesForSelection(Collection<ComponentState> values) {
        boolean allCandidates = true;
        for (ComponentState value : values) {
            if (!value.isCandidateForConflictResolution()) {
                allCandidates = false;
                break;
            }
        }
        return allCandidates;
    }

    public ComponentState getSelected() {
        return selected;
    }

    public void select(ComponentState selected) {
        assert this.selected == null;
        this.selected = selected;
        for (ComponentState version : versions.values()) {
            version.evict();
        }
        selected.select();
    }

    public ComponentState clearSelection() {
        ComponentState previousSelection = selected;
        selected = null;
        for (ComponentState version : versions.values()) {
            if (version.isSelected()) {
                version.makeSelectable();
            }
        }
        return previousSelection;
    }

    public void restart(ComponentState selected) {
        if (this.selected != selected) {
            select(selected);
            doRestart(selected);
        }
    }

    public void softSelect(ComponentState selected) {
        assert this.selected == null;
        this.selected = selected;
        for (ComponentState version : versions.values()) {
            version.makeSelectable();
        }
        selected.select();
        doRestart(selected);
    }

    private void doRestart(ComponentState selected) {
        for (ComponentState version : versions.values()) {
            version.restart(selected);
        }
        for (SelectorState selector : selectors) {
            selector.restart(selected);
        }
        if (!unattachedDependencies.isEmpty()) {
            restartUnattachedDependencies(selected);
        }
    }

    private void restartUnattachedDependencies(ComponentState selected) {
        if (unattachedDependencies.size()==1) {
            unattachedDependencies.get(0).restart(selected);
        } else {
            for (EdgeState dependency : new ArrayList<EdgeState>(unattachedDependencies)) {
                dependency.restart(selected);
            }
        }
        unattachedDependencies.clear();
    }

    public void addUnattachedDependency(EdgeState edge) {
        unattachedDependencies.add(edge);
    }

    public void removeUnattachedDependency(EdgeState edge) {
        unattachedDependencies.remove(edge);
    }

    public ComponentState getVersion(ModuleVersionIdentifier id) {
        ComponentState moduleRevision = versions.get(id);
        if (moduleRevision == null) {
            moduleRevision = new ComponentState(idGenerator.generateId(), this, id, metaDataResolver);
            versions.put(id, moduleRevision);
        }
        return moduleRevision;
    }

    public void addSelector(SelectorState selector) {
        selectors.add(selector);
    }

    public Set<SelectorState> getSelectors() {
        return selectors;
    }
}
