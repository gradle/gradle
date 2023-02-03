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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.GroupExclude;
import org.gradle.internal.component.model.IvyArtifactName;

final class DefaultGroupExclude implements GroupExclude {
    private final String group;
    private final int hashCode;

    private DefaultGroupExclude(String group) {
        this.group = group;
        this.hashCode = group.hashCode();
    }

    static GroupExclude of(String group) {
        return new DefaultGroupExclude(group);
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public boolean excludes(ModuleIdentifier module) {
        return group.equals(module.getGroup());
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

        DefaultGroupExclude that = (DefaultGroupExclude) o;

        if (hashCode != that.hashCode) {
            return false;
        }
        return group.equals(that.group);

    }

    @Override
    public int hashCode() {
        return group.hashCode();
    }

    @Override
    public String toString() {
        return "{\"exclude group\" : \"" + group + "\"}";
    }
}
