/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;

import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedDependency implements ResolvedDependency {
    private Set<ResolvedDependency> children = new LinkedHashSet<ResolvedDependency>();
    private Set<ResolvedDependency> parents = new LinkedHashSet<ResolvedDependency>();
    private Map<ResolvedDependency, Set<ResolvedArtifact>> parentArtifacts
            = new LinkedHashMap<ResolvedDependency, Set<ResolvedArtifact>>();
    private String name;
    private final ResolvedConfigurationIdentifier id;
    private Set<ResolvedArtifact> moduleArtifacts = new LinkedHashSet<ResolvedArtifact>();
    private Map<ResolvedDependency, Set<ResolvedArtifact>> allArtifactsCache = new HashMap<ResolvedDependency, Set<ResolvedArtifact>>();
    private Set<ResolvedArtifact> allModuleArtifactsCache;

    public DefaultResolvedDependency(String name, String moduleGroup, String moduleName, String moduleVersion,
                                     String configuration, Set<ResolvedArtifact> moduleArtifacts) {
        assert name != null;
        assert moduleArtifacts != null;

        this.name = name;
        id = new ResolvedConfigurationIdentifier(moduleGroup, moduleName, moduleVersion, configuration);
        this.moduleArtifacts = moduleArtifacts;
    }

    public DefaultResolvedDependency(String moduleGroup, String moduleName, String moduleVersion, String configuration,
                                     Set<ResolvedArtifact> moduleArtifacts) {
        this(moduleGroup + ":" + moduleName + ":" + moduleVersion, moduleGroup, moduleName, moduleVersion,
                configuration, moduleArtifacts);
    }

    public String getName() {
        return name;
    }

    public ResolvedConfigurationIdentifier getId() {
        return id;
    }

    public String getModuleGroup() {
        return id.getModuleGroup();
    }

    public String getModuleName() {
        return id.getModuleName();
    }

    public String getModuleVersion() {
        return id.getModuleVersion();
    }

    public String getConfiguration() {
        return id.getConfiguration();
    }

    public Set<ResolvedDependency> getChildren() {
        return children;
    }

    public Set<ResolvedArtifact> getModuleArtifacts() {
        return moduleArtifacts;
    }

    public Set<ResolvedArtifact> getAllModuleArtifacts() {
        if (allModuleArtifactsCache == null) {
            Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<ResolvedArtifact>();
            allArtifacts.addAll(getModuleArtifacts());
            for (ResolvedDependency childResolvedDependency : getChildren()) {
                allArtifacts.addAll(childResolvedDependency.getAllModuleArtifacts());
            }
            allModuleArtifactsCache = allArtifacts;
        }
        return allModuleArtifactsCache;
    }

    public Set<ResolvedArtifact> getParentArtifacts(ResolvedDependency parent) {
        if (!parents.contains(parent)) {
            throw new InvalidUserDataException("Unknown Parent");
        }
        Set<ResolvedArtifact> artifacts = parentArtifacts.get(parent);
        return artifacts == null ? Collections.<ResolvedArtifact>emptySet() : artifacts;
    }

    public Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent) {
        return GUtil.addSets(getParentArtifacts(parent), getModuleArtifacts());
    }

    public Set<ResolvedArtifact> getAllArtifacts(ResolvedDependency parent) {
        if (allArtifactsCache.get(parent) == null) {
            Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<ResolvedArtifact>();
            allArtifacts.addAll(getArtifacts(parent));
            for (ResolvedDependency childResolvedDependency : getChildren()) {
                for (ResolvedDependency childParent : childResolvedDependency.getParents()) {
                    allArtifacts.addAll(childResolvedDependency.getAllArtifacts(childParent));
                }
            }
            allArtifactsCache.put(parent, allArtifacts);
        }
        return allArtifactsCache.get(parent);
    }

    public Set<ResolvedDependency> getParents() {
        return parents;
    }

    public String toString() {
        return name + ";" + getConfiguration();
    }

    public void addParentSpecificArtifacts(ResolvedDependency parent, Set<ResolvedArtifact> artifacts) {
        parentArtifacts.put(parent, artifacts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultResolvedDependency that = (DefaultResolvedDependency) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    public void addChild(DefaultResolvedDependency child) {
        children.add(child);
        child.parents.add(this);
    }
}
