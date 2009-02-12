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
package org.gradle.api.artifacts;

import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

/**
 * @author Hans Dockter
 */
public class ResolveInstruction {
    private Spec<Dependency> dependencySpec = Specs.SATISFIES_ALL;

    private boolean transitive = true;

    private boolean failOnResolveError = true;

    public ResolveInstruction() {
    }

    public ResolveInstruction(ResolveInstruction resolveInstruction) {
        dependencySpec = resolveInstruction.getDependencySpec();
        transitive = resolveInstruction.isTransitive();
        failOnResolveError = resolveInstruction.isFailOnResolveError();
    }

    public Spec<Dependency> getDependencySpec() {
        return dependencySpec;
    }

    public ResolveInstruction setDependencySpec(Spec<Dependency> dependencySpec) {
        this.dependencySpec = dependencySpec;
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
        if (dependencySpec != null ? !dependencySpec.equals(that.dependencySpec) : that.dependencySpec != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = dependencySpec != null ? dependencySpec.hashCode() : 0;
        result = 31 * result + (transitive ? 1 : 0);
        result = 31 * result + (failOnResolveError ? 1 : 0);
        return result;
    }
}
