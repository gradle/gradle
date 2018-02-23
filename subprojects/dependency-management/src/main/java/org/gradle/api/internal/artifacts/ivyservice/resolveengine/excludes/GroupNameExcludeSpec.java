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

/**
 * A ModuleResolutionFilter that excludes any module with a matching group.
 * Does not exclude any artifacts.
 */
class GroupNameExcludeSpec extends AbstractModuleExclusion {
    final String group;

    GroupNameExcludeSpec(String group) {
        this.group = group;
    }

    @Override
    public String toString() {
        return "{group " + group + "}";
    }

    @Override
    protected boolean doEquals(Object o) {
        GroupNameExcludeSpec other = (GroupNameExcludeSpec) o;
        return group.equals(other.group);
    }

    @Override
    protected int doHashCode() {
        return group.hashCode();
    }

    @Override
    public boolean doExcludesSameModulesAs(AbstractModuleExclusion other) {
        GroupNameExcludeSpec groupNameExcludeSpec = (GroupNameExcludeSpec) other;
        return group.equals(groupNameExcludeSpec.group);
    }

    @Override
    public boolean excludeModule(ModuleIdentifier module) {
        return module.getGroup().equals(group);
    }
}
