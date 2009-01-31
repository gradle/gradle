/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.dependencies;

import org.gradle.api.filter.FilterSpec;
import org.gradle.api.filter.Filters;

/**
 * @author Hans Dockter
 */
public class ResolveInstruction {
    private FilterSpec<Dependency> dependencyFilter = Filters.NO_FILTER;

    private boolean transitive = true;

    private boolean failOnResolveError = true;

    public ResolveInstruction() {
    }

    public ResolveInstruction(ResolveInstruction resolveInstruction) {
        dependencyFilter = resolveInstruction.getDependencyFilter();
        transitive = resolveInstruction.isTransitive();
        failOnResolveError = resolveInstruction.isFailOnResolveError();
    }

    public FilterSpec<Dependency> getDependencyFilter() {
        return dependencyFilter;
    }

    public ResolveInstruction setDependencyFilter(FilterSpec<Dependency> dependencyFilter) {
        this.dependencyFilter = dependencyFilter;
        return this;
    }
    
    public boolean isTransitive() {
        return transitive;
    }

    public ResolveInstruction setTransitive(boolean transitive) {
        this.transitive = transitive;
        return this;
    }

    public boolean isFailOnResolveError() {
        return failOnResolveError;
    }

    public ResolveInstruction setFailOnResolveError(boolean failOnResolveError) {
        this.failOnResolveError = failOnResolveError;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ResolveInstruction that = (ResolveInstruction) o;

        if (failOnResolveError != that.failOnResolveError) return false;
        if (transitive != that.transitive) return false;
        if (dependencyFilter != null ? !dependencyFilter.equals(that.dependencyFilter) : that.dependencyFilter != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = dependencyFilter != null ? dependencyFilter.hashCode() : 0;
        result = 31 * result + (transitive ? 1 : 0);
        result = 31 * result + (failOnResolveError ? 1 : 0);
        return result;
    }
}
