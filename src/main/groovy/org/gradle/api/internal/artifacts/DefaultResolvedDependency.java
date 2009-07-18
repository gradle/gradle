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
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedDependency implements ResolvedDependency {
    private Set<ResolvedDependency> children = new HashSet<ResolvedDependency>();
    private Set<ResolvedDependency> parents = new HashSet<ResolvedDependency>();
    private String configuration;
    private Set<File> moduleFiles = new LinkedHashSet<File>();
    private Map<ResolvedDependency, Set<File>> parentFiles = new LinkedHashMap<ResolvedDependency, Set<File>>();
    private String name;
    private Set<String> configurationHierarchy;

    public DefaultResolvedDependency(String name, String configuration, Set<String> configurationHierarchy, Set<File> moduleFiles) {
        this.configurationHierarchy = configurationHierarchy;
        assert moduleFiles != null;
        this.name = name;
        this.configuration = configuration;
        this.moduleFiles = moduleFiles;
    }

    public String getName() {
        return name;
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

    public Set<File> getModuleFiles() {
        return moduleFiles;
    }

    public Set<File> getAllModuleFiles() {
        Set<File> allFiles = new LinkedHashSet<File>();
        allFiles.addAll(getModuleFiles());
        for (ResolvedDependency childResolvedDependency : getChildren()) {
            allFiles.addAll(childResolvedDependency.getAllModuleFiles());
        }
        return allFiles;
    }

    public Set<File> getParentFiles(ResolvedDependency parent) {
        throwExceptionIfUnknownParent(parent);
        Set<File> files = parentFiles.get(parent);
        return files == null ? Collections.<File>emptySet() : files;
    }

    private void throwExceptionIfUnknownParent(ResolvedDependency parent) {
        if (!parents.contains(parent)) {
            throw new InvalidUserDataException("Unknown Parent");
        }
    }

    public Set<File> getFiles(ResolvedDependency parent) {
        throwExceptionIfUnknownParent(parent);
        return GUtil.addSets(getParentFiles(parent), getModuleFiles());
    }

    public Set<File> getAllFiles(ResolvedDependency parent) {
        throwExceptionIfUnknownParent(parent);
        Set<File> allFiles = new LinkedHashSet<File>();
        allFiles.addAll(getFiles(parent));
        for (ResolvedDependency childResolvedDependency : getChildren()) {
            for (ResolvedDependency childParent : childResolvedDependency.getParents()) {
                allFiles.addAll(childResolvedDependency.getAllFiles(childParent));
            }
        }
        return allFiles;
    }

    public Set<ResolvedDependency> getParents() {
        return parents;
    }

    public String toString() {
        return name + ";" + configuration;
    }

    public void addParentSpecificFiles(ResolvedDependency parent, Set<File> files) {
        parentFiles.put(parent, files);
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
