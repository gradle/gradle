/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.simple;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ModuleIdSetExclude;
import org.gradle.internal.collect.PersistentSet;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultModuleIdSetExclude implements ModuleIdSetExclude {
    private final PersistentSet<ModuleIdentifier> moduleIds;
    private final int hashCode;

    static ModuleIdSetExclude of(PersistentSet<ModuleIdentifier> ids) {
        return new DefaultModuleIdSetExclude(ids);
    }

    private DefaultModuleIdSetExclude(PersistentSet<ModuleIdentifier> moduleIds) {
        this.moduleIds = moduleIds;
        this.hashCode = moduleIds.hashCode();
    }

    @Override
    public PersistentSet<ModuleIdentifier> getModuleIds() {
        return moduleIds;
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return moduleIds.contains(module);
    }

    @Override
    public boolean excludesArtifact(ModuleIdentifier module, IvyArtifactName artifactName) {
        return false;
    }

    @Override
    public boolean mayExcludeArtifacts() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleIdSetExclude that = (DefaultModuleIdSetExclude) o;

        return moduleIds.equals(that.moduleIds);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "{ \"module ids\" : [" + ExcludeJsonHelper.toJson(moduleIds) + "]}";
    }

}
