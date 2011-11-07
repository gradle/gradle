/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ModuleIdentifier;

//TODO SF - can we call it DefaultModuleIdentifier or something similar?
public class DefaultResolvedModuleId implements ModuleIdentifier {
    private String group;
    private String name;
    private String version;

    public DefaultResolvedModuleId(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public DefaultResolvedModuleId() {}

    public String getGroup() {
        return group;
    }

    public DefaultResolvedModuleId setGroup(String group) {
        this.group = group;
        return this;
    }

    public String getName() {
        return name;
    }

    public DefaultResolvedModuleId setName(String name) {
        this.name = name;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public DefaultResolvedModuleId setVersion(String version) {
        this.version = version;
        return this;
    }

    @Override
    public String toString() {
        //TODO SF - can we slightly change the format because it clashes with Map
        return String.format("[group: %s, module: %s, version: %s]", group, name, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultResolvedModuleId other = (DefaultResolvedModuleId) obj;
        if (!group.equals(other.group)) {
            return false;
        }
        if (!name.equals(other.name)) {
            return false;
        }
        if (!version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return group.hashCode() ^ name.hashCode() ^ version.hashCode();
    }
}
