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

package org.gradle.api.internal.artifacts.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
* by Szczepan Faber, created at: 8/10/12
*/
public class DefaultResolvedModuleVersionResult implements ResolvedModuleVersionResult {
    private final ModuleVersionIdentifier id;
    private final Set<DependencyResult> dependencies = new LinkedHashSet<DependencyResult>();
    private final Set<ResolvedDependencyResult> dependents = new LinkedHashSet<ResolvedDependencyResult>();
    private final ModuleVersionSelectionReason selectionReason;

    public DefaultResolvedModuleVersionResult(ModuleVersionIdentifier id) {
        this(id, VersionSelectionReasons.REQUESTED);
    }

    public DefaultResolvedModuleVersionResult(ModuleVersionIdentifier id, ModuleVersionSelectionReason selectionReason) {
        assert id != null;
        assert selectionReason != null;

        this.id = id;
        this.selectionReason = selectionReason;
    }

    public ModuleVersionIdentifier getId() {
        return id;
    }

    public Set<DependencyResult> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public Set<ResolvedDependencyResult> getDependents() {
        return Collections.unmodifiableSet(dependents);
    }

    public DefaultResolvedModuleVersionResult addDependency(DependencyResult dependency) {
        this.dependencies.add(dependency);
        return this;
    }

    public DefaultResolvedModuleVersionResult addDependent(ResolvedDependencyResult dependent) {
        this.dependents.add(dependent);
        return this;
    }

    public ModuleVersionSelectionReason getSelectionReason() {
        return selectionReason;
    }

    @Override
    public String toString() {
        return id.getGroup() + ":" + id.getName() + ":" + id.getVersion();
    }
}
