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

import org.apache.ivy.core.module.id.ArtifactId;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

/**
 * A ModuleResolutionFilter that accepts any artifact that doesn't match the exclude rule.
 * Accepts all modules.
 */
class ArtifactExcludeSpec extends AbstractModuleExcludeRuleFilter {
    private final ModuleIdentifier moduleId;
    private final IvyArtifactName ivyArtifactName;

    ArtifactExcludeSpec(ArtifactId artifactId) {
        this.moduleId = DefaultModuleIdentifier.newId(artifactId.getModuleId().getOrganisation(), artifactId.getModuleId().getName());
        this.ivyArtifactName = new DefaultIvyArtifactName(artifactId.getName(), artifactId.getType(), artifactId.getExt());
    }

    @Override
    public String toString() {
        return String.format("{artifact %s:%s}", moduleId, ivyArtifactName);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        ArtifactExcludeSpec other = (ArtifactExcludeSpec) o;
        return moduleId.equals(other.moduleId) && ivyArtifactName.equals(other.ivyArtifactName);
    }

    @Override
    public int hashCode() {
        return moduleId.hashCode() ^ ivyArtifactName.hashCode();
    }

    @Override
    protected boolean doAcceptsSameModulesAs(AbstractModuleExcludeRuleFilter other) {
        return true;
    }

    @Override
    protected boolean acceptsAllModules() {
        return true;
    }

    public boolean acceptModule(ModuleIdentifier module) {
        return true;
    }

    public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        return !(matches(moduleId.getGroup(), module.getGroup())
                && matches(moduleId.getName(), module.getName())
                && matches(ivyArtifactName.getName(), artifact.getName())
                && matches(ivyArtifactName.getExtension(), artifact.getExtension())
                && matches(ivyArtifactName.getType(), artifact.getType()));
    }

    public boolean acceptsAllArtifacts() {
        return false;
    }

    private boolean matches(String expression, String input) {
        return isWildcard(expression) || expression.equals(input);
    }
}
