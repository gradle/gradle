/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
* by Szczepan Faber, created at: 8/10/12
*/
public class DefaultResolvedModuleVersionResult implements ResolvedModuleVersionResult {
    private final ModuleVersionIdentifier id;
    private final Set<DefaultResolvedDependencyResult> dependencies = new LinkedHashSet<DefaultResolvedDependencyResult>();

    public DefaultResolvedModuleVersionResult(ModuleVersionIdentifier id) {
        assert id != null;
        this.id = id;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public Set<DefaultResolvedDependencyResult> getDependencies() {
        return dependencies;
    }

    public DefaultResolvedModuleVersionResult addDependency(DefaultResolvedDependencyResult dependencyResult) {
        this.dependencies.add(dependencyResult);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolvedModuleVersionResult)) {
            return false;
        }

        ResolvedModuleVersionResult that = (ResolvedModuleVersionResult) o;

        return id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
