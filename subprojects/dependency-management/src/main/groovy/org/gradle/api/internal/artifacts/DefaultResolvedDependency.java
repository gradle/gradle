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

import org.apache.commons.lang.ObjectUtils;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.ResolvedModuleVersion;

import java.util.*;

public class DefaultResolvedDependency implements ResolvedDependency {
    private final Set<ResolvedDependency> children = new LinkedHashSet<ResolvedDependency>();
    private final Set<ResolvedDependency> parents = new LinkedHashSet<ResolvedDependency>();
    private final Map<ResolvedDependency, Set<ResolvedArtifact>> parentArtifacts = new LinkedHashMap<ResolvedDependency, Set<ResolvedArtifact>>();
    private final String name;
    private final ResolvedConfigurationIdentifier id;
    private final Set<ResolvedArtifact> moduleArtifacts;
    private final Map<ResolvedDependency, Set<ResolvedArtifact>> allArtifactsCache = new HashMap<ResolvedDependency, Set<ResolvedArtifact>>();
    private Set<ResolvedArtifact> allModuleArtifactsCache;

    public DefaultResolvedDependency(ModuleVersionIdentifier moduleVersionIdentifier, String configuration) {
        this.name = String.format("%s:%s:%s", moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName(), moduleVersionIdentifier.getVersion());
        id = new ResolvedConfigurationIdentifier(moduleVersionIdentifier, configuration);
        this.moduleArtifacts = new TreeSet<ResolvedArtifact>(new ResolvedArtifactComparator());
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

    public ResolvedModuleVersion getModule() {
        return new ResolvedModuleVersion() {
            public ModuleVersionIdentifier getId() {
                return id.getId();
            }
        };
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
            throw new InvalidUserDataException("Provided dependency (" + parent + ") must be a parent of: " + this);
        }
        Set<ResolvedArtifact> artifacts = parentArtifacts.get(parent);
        return artifacts == null ? Collections.<ResolvedArtifact>emptySet() : artifacts;
    }

    public Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent) {
        return getParentArtifacts(parent);
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

    public void addParentSpecificArtifacts(ResolvedDependency parent, Set<ResolvedArtifact> artifacts) {
        Set<ResolvedArtifact> parentArtifacts = this.parentArtifacts.get(parent);
        if (parentArtifacts == null) {
            parentArtifacts = new TreeSet<ResolvedArtifact>(new ResolvedArtifactComparator());
            this.parentArtifacts.put(parent, parentArtifacts);
        }
        parentArtifacts.addAll(artifacts);
        moduleArtifacts.addAll(artifacts);
    }

    public void addModuleArtifact(ResolvedArtifact artifact) {
        moduleArtifacts.add(artifact);
    }

    private static class ResolvedArtifactComparator implements Comparator<ResolvedArtifact> {
        public int compare(ResolvedArtifact artifact1, ResolvedArtifact artifact2) {
            int diff = artifact1.getName().compareTo(artifact2.getName());
            if (diff != 0) {
                return diff;
            }
            diff = ObjectUtils.compare(artifact1.getClassifier(), artifact2.getClassifier());
            if (diff != 0) {
                return diff;
            }
            diff = artifact1.getExtension().compareTo(artifact2.getExtension());
            if (diff != 0) {
                return diff;
            }
            diff = artifact1.getType().compareTo(artifact2.getType());
            if (diff != 0) {
                return diff;
            }
            // Use an arbitrary ordering when the artifacts have the same public attributes
            return artifact1.hashCode() - artifact2.hashCode();
        }
    }
}
