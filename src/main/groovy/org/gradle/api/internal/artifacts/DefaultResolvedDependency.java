/*
 * Copyright 2009 the original author or authors.
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
    private Set<ResolvedDependency> children = new HashSet<ResolvedDependency>();
    private Set<ResolvedDependency> parents = new HashSet<ResolvedDependency>();
    private String configuration;
    private Set<ResolvedArtifact> moduleArtifacts = new LinkedHashSet<ResolvedArtifact>();
    private Map<ResolvedDependency, Set<ResolvedArtifact>> parentArtifacts = new LinkedHashMap<ResolvedDependency, Set<ResolvedArtifact>>();
    private String group;
    private String name;
    private String version;
    private Set<String> configurationHierarchy;

    public DefaultResolvedDependency(String group, String name, String version, String configuration, Set<String> configurationHierarchy,
                                     Set<ResolvedArtifact> moduleArtifacts) {
        this.group = group;
        this.version = version;
        this.configurationHierarchy = configurationHierarchy;
        assert moduleArtifacts != null;
        this.name = name;
        this.configuration = configuration;
        this.moduleArtifacts = moduleArtifacts;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getConfiguration() {
        return configuration;
    }

    public Set<String> getConfigurationHierarchy() {
        return configurationHierarchy;
    }

    public boolean containsConfiguration(String configuration) {
        return configurationHierarchy.contains(configuration);
    }

    public Set<ResolvedDependency> getChildren() {
        return children;
    }

    public Set<ResolvedArtifact> getModuleArtifacts() {
        return moduleArtifacts;
    }

    public Set<ResolvedArtifact> getAllModuleArtifacts() {
        Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<ResolvedArtifact>();
        allArtifacts.addAll(getModuleArtifacts());
        for (ResolvedDependency childResolvedDependency : getChildren()) {
            allArtifacts.addAll(childResolvedDependency.getAllModuleArtifacts());
        }
        return allArtifacts;
    }

    public Set<ResolvedArtifact> getParentArtifacts(ResolvedDependency parent) {
        throwExceptionIfUnknownParent(parent);
        Set<ResolvedArtifact> artifacts = parentArtifacts.get(parent);
        return artifacts == null ? Collections.<ResolvedArtifact>emptySet() : artifacts;
    }

    public Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent) {
        throwExceptionIfUnknownParent(parent);
        return GUtil.addSets(getParentArtifacts(parent), getModuleArtifacts());
    }

    public Set<ResolvedArtifact> getAllArtifacts(ResolvedDependency parent) {
        throwExceptionIfUnknownParent(parent);
        Set<ResolvedArtifact> allArtifacts = new LinkedHashSet<ResolvedArtifact>();
        allArtifacts.addAll(getArtifacts(parent));
        for (ResolvedDependency childResolvedDependency : getChildren()) {
            for (ResolvedDependency childParent : childResolvedDependency.getParents()) {
                allArtifacts.addAll(childResolvedDependency.getAllArtifacts(childParent));
            }
        }
        return allArtifacts;
    }

    private void throwExceptionIfUnknownParent(ResolvedDependency parent) {
        if (!parents.contains(parent)) {
            throw new InvalidUserDataException("Unknown Parent");
        }
    }
    
    public Set<ResolvedDependency> getParents() {
        return parents;
    }

    public String toString() {
        return name + ";" + configuration;
    }

    public void addParentSpecificArtifacts(ResolvedDependency parent, Set<ResolvedArtifact> artifacts) {
        parentArtifacts.put(parent, artifacts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DefaultResolvedDependency that = (DefaultResolvedDependency) o;

        if (configuration != null ? !configuration.equals(that.configuration) : that.configuration != null)
            return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = configuration != null ? configuration.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        return result;
    }
}
