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
 * A ModuleResolutionFilter that accepts any module that has a group other than the one specified.
 * Accepts all artifacts.
 */
class GroupNameExcludeSpec extends AbstractModuleExcludeRuleFilter {
    final String group;

    GroupNameExcludeSpec(String group) {
        this.group = group;
    }

    @Override
    public String toString() {
        return String.format("{group %s}", group);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        GroupNameExcludeSpec other = (GroupNameExcludeSpec) o;
        return group.equals(other.group);
    }

    @Override
    public int hashCode() {
        return group.hashCode();
    }

    @Override
    public boolean doAcceptsSameModulesAs(AbstractModuleExcludeRuleFilter other) {
        GroupNameExcludeSpec groupNameExcludeSpec = (GroupNameExcludeSpec) other;
        return group.equals(groupNameExcludeSpec.group);
    }

    public boolean acceptModule(ModuleIdentifier element) {
        return !element.getGroup().equals(group);
    }

    public boolean acceptArtifact(ModuleIdentifier module, IvyArtifactName artifact) {
        return true;
    }

    public boolean acceptsAllArtifacts() {
        return true;
    }
}
