/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * A ModuleResolutionFilter that excludes any artifact that matches supplied module and artifact details.
 * Does not exclude any modules
 */
class ArtifactExcludeSpec extends AbstractModuleExclusion {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName ivyArtifactName;

    ArtifactExcludeSpec(ModuleIdentifier moduleId, IvyArtifactName artifact) {
        this.moduleId = moduleId;
        this.ivyArtifactName = artifact;
    }

    @Override
    public String toString() {
        return "{artifact " + moduleId + ":" + ivyArtifactName + "}";
    }

    @Override
    protected boolean doEquals(Object o) {
        ArtifactExcludeSpec other = (ArtifactExcludeSpec) o;
        return moduleId.equals(other.moduleId) && ivyArtifactName.equals(other.ivyArtifactName);
    }

    @Override
    protected int doHashCode() {
        return moduleId.hashCode() ^ ivyArtifactName.hashCode();
    }

    @Override
    protected boolean doExcludesSameModulesAs(AbstractModuleExclusion other) {
        return true;
    }

    @Override
    protected boolean excludesNoModules() {
        return true;
    }

    @Override
    public boolean excludeModule(ModuleIdentifier module) {
        return false;
    }

    @Override
    public boolean excludeArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        return matches(moduleId.getGroup(), module.getGroup())
            && matches(moduleId.getName(), module.getName())
            && matches(ivyArtifactName.getName(), artifact.getName())
            && matches(ivyArtifactName.getExtension(), artifact.getExtension())
            && matches(ivyArtifactName.getType(), artifact.getType());
    }

    public boolean mayExcludeArtifacts() {
        return true;
    }

    private boolean matches(String expression, String input) {
        return isWildcard(expression) || expression.equals(input);
    }
}
