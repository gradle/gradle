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

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultResolvedDependency implements ResolvedDependency {
    private Set<ResolvedDependency> children = new HashSet<ResolvedDependency>();
    private Set<ResolvedDependency> parents = new HashSet<ResolvedDependency>();
    private String configuration;
    private Set<File> files = new LinkedHashSet<File>();

    private String name;

    public DefaultResolvedDependency(String name, String configuration, Set<File> files) {
        this.name = name;
        this.configuration = configuration;
        this.files = files;
    }

    public String getName() {
        return name;
    }

    public String getConfiguration() {
        return configuration;
    }

    public Set<ResolvedDependency> getChildren() {
        return children;
    }

    public Set<File> getFiles() {
        return files;
    }

    public Set<File> getAllFiles() {
        Set<File> allFiles = new LinkedHashSet<File>();
        allFiles.addAll(getFiles());
        for (ResolvedDependency childResolvedDependency : getChildren()) {
            allFiles.addAll(childResolvedDependency.getAllFiles());
        }
        return allFiles;
    }

    public Set<ResolvedDependency> getParents() {
        return parents;
    }

    public String toString() {
        return name + ";" + configuration;
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
