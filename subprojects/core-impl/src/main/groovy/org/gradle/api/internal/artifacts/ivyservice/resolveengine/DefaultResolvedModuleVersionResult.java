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
import org.gradle.api.internal.dependencygraph.api.ResolvedModuleVersionResult;

import java.util.LinkedHashSet;
import java.util.Set;

/**
* by Szczepan Faber, created at: 8/10/12
*/
public class DefaultResolvedModuleVersionResult implements ResolvedModuleVersionResult {
    private final ModuleVersionIdentifier id;
    private final Set<DefaultResolvedDependencyResult> dependencies = new LinkedHashSet<DefaultResolvedDependencyResult>();

    public DefaultResolvedModuleVersionResult(ModuleVersionIdentifier id) {
        this.id = id;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public Set<DefaultResolvedDependencyResult> getDependencies() {
        return dependencies;
    }

    //TODO SF tests
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolvedModuleVersionResult)) {
            return false;
        }

        ResolvedModuleVersionResult that = (ResolvedModuleVersionResult) o;

        if (dependencies != null ? !dependencies.equals(that.getDependencies()) : that.getDependencies() != null) {
            return false;
        }
        if (id != null ? !id.equals(that.getId()) : that.getId() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (dependencies != null ? dependencies.hashCode() : 0);
        return result;
    }
}
